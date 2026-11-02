package org.jacamo.checkpoint.runtime;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/** Shared source admission and logical time. No domain, transport or UI dependency. */
public final class ExecutionBarrier {
    public static final String VERSION="1.0.0";
    private static final ReentrantLock lock=new ReentrantLock();
    private static final Condition changed=lock.newCondition();
    private static final ThreadLocal<Integer> depth=ThreadLocal.withInitial(()->0);
    private static final Map<Long,String> active=new LinkedHashMap<>();
    private static final Map<String,Long> completed=new TreeMap<>();
    private static final IdentityHashMap<Object,Boolean> admittedQueue=new IdentityHashMap<>();
    private static volatile boolean enabled;
    private static boolean closed, frozen, stopped;
    private static String run="";
    private static long sequence, epoch, pending, originWall, originNanos, heldNanos, freezeNanos;
    private static final PriorityQueue<Timed> timers=new PriorityQueue<>(Comparator.comparingLong(Timed::due).thenComparingLong(Timed::order));
    private static long timerSequence;
    private static Thread timerWorker;
    private record Timed(long due,long order,String run,Runnable action) { }
    public record Proof(String run,long epoch,long sequence,long sourceMillis,boolean admissionClosed,
                        boolean clockFrozen,Map<Long,String> active,long pending,Map<String,Long> completed) {
        public Proof {active=Map.copyOf(active);completed=Map.copyOf(completed);}
    }
    public static final class Stopped extends IllegalStateException { public Stopped(){super("CHECKPOINT_RUN_STOPPED");} }
    private ExecutionBarrier(){ }

    public static void enable(String identity) {
        lock.lock();try {
            if(identity==null||identity.isBlank()||enabled)throw new IllegalStateException("CHECKPOINT_RUN_ALREADY_OWNED_OR_INVALID");
            run=identity;originWall=System.currentTimeMillis();originNanos=System.nanoTime();enabled=true;
        }finally{lock.unlock();}
    }
    public static boolean enabled(){return enabled;}
    public static long originMillis(){lock.lock();try{requireEnabled();return originWall;}finally{lock.unlock();}}
    public static Step enter(String lane) {
        if(!enabled)return new Step(false,lane);
        lock.lock();try {
            int n=depth.get();
            while(n==0&&closed&&!stopped)changed.awaitUninterruptibly();
            if(stopped)throw new Stopped();
            if(n==0)active.put(Thread.currentThread().threadId(),lane);
            depth.set(n+1);return new Step(true,lane);
        }finally{lock.unlock();}
    }
    /** Nonblocking queue admission, safe while a producer holds a source lock. */
    public static void queued(Object work) {
        if(!enabled)return;
        lock.lock();try {
            if(stopped)throw new Stopped();
            if((!closed||depth.get()>0)&&!admittedQueue.containsKey(work)){admittedQueue.put(work,true);pending++;changed.signalAll();}
        }finally{lock.unlock();}
    }
    /** Drain admitted queued work and its causal children even after admission closes. */
    public static Step enterQueued(Object work,String lane) {
        if(!enabled)return new Step(false,lane);
        lock.lock();try {
            while(depth.get()==0&&closed&&!admittedQueue.containsKey(work)&&!stopped)changed.awaitUninterruptibly();
            if(stopped)throw new Stopped();
            if(admittedQueue.remove(work)!=null) {
                pending--;int n=depth.get();if(n==0)active.put(Thread.currentThread().threadId(),lane);depth.set(n+1);changed.signalAll();return new Step(true,lane);
            }
            int n=depth.get();if(n==0)active.put(Thread.currentThread().threadId(),lane);depth.set(n+1);return new Step(true,lane);
        }finally{lock.unlock();}
    }
    public static final class Step implements AutoCloseable {
        private final boolean owned;private final String lane;private boolean done;
        private Step(boolean owned,String lane){this.owned=owned;this.lane=lane;}
        @Override public void close(){if(!owned||done)return;done=true;leave(lane);}
    }
    private static void leave(String lane) {
        lock.lock();try {
            int n=depth.get();if(n<1)throw new IllegalStateException("CHECKPOINT_STEP_UNBALANCED");
            depth.set(n-1);
            if(n==1){active.remove(Thread.currentThread().threadId());completed.put(lane,++sequence);changed.signalAll();}
        }finally{lock.unlock();}
    }
    /** Call before releasing a source lock; resume() must be outside every source lock. */
    public static Suspension suspend() {
        if(!enabled)return new Suspension(0,"");
        lock.lock();try {
            int n=depth.get();String lane=active.remove(Thread.currentThread().threadId());
            if(n<1)throw new IllegalStateException("CHECKPOINT_AWAIT_OUTSIDE_STEP");
            depth.set(0);completed.put(lane,++sequence);changed.signalAll();return new Suspension(n,lane);
        }finally{lock.unlock();}
    }
    public static final class Suspension {
        private final int count;private final String lane;private boolean resumed;
        private Suspension(int count,String lane){this.count=count;this.lane=lane;}
        public void resume() {
            if(count==0||resumed)return;
            lock.lock();try {
                while(closed&&!stopped)changed.awaitUninterruptibly();
                // Restore the original stack even on cancellation so finally/close
                // can unwind platform locks and balance its original permits.
                depth.set(count);active.put(Thread.currentThread().threadId(),lane);resumed=true;
                if(stopped)throw new Stopped();
            }finally{lock.unlock();}
        }
    }
    /** Nonblocking; safe at a source callback boundary. Waiting is a coordinator-worker job. */
    public static long closeAdmission() {
        lock.lock();try {
            requireEnabled();if(stopped)throw new Stopped();
            if(!closed){closed=true;epoch++;changed.signalAll();}return epoch;
        }finally{lock.unlock();}
    }
    public static Proof awaitFrozen(long expectedEpoch,Duration timeout) throws InterruptedException {
        if(depth.get()!=0)throw new IllegalStateException("CHECKPOINT_CANNOT_WAIT_INSIDE_SOURCE_STEP");
        long remaining=timeout.toNanos();lock.lockInterruptibly();try {
            requireEnabled();
            while(!stopped&&closed&&epoch==expectedEpoch&&(!active.isEmpty()||pending!=0)&&remaining>0)
                remaining=changed.awaitNanos(remaining);
            if(stopped)throw new Stopped();
            if(!closed||epoch!=expectedEpoch)throw new IllegalStateException("CHECKPOINT_EPOCH_REPLACED");
            if(!active.isEmpty()||pending!=0)throw new IllegalStateException("CHECKPOINT_DRAIN_TIMEOUT:"+active+":pending="+pending);
            if(!frozen){freezeNanos=System.nanoTime();frozen=true;changed.signalAll();}
            return proofLocked();
        }finally{lock.unlock();}
    }
    public static void release(long expectedEpoch) {
        lock.lock();try {
            if(stopped||!closed||!frozen||epoch!=expectedEpoch)throw new IllegalStateException("CHECKPOINT_RELEASE_NOT_EXACT_FROZEN_EPOCH");
            heldNanos+=System.nanoTime()-freezeNanos;frozen=false;closed=false;changed.signalAll();
        }finally{lock.unlock();}
    }
    public static Proof proof(){lock.lock();try{return proofLocked();}finally{lock.unlock();}}
    private static Proof proofLocked(){return new Proof(run,epoch,sequence,nowLocked(),closed,frozen,active,pending,completed);}
    public static long nowMillis(){if(!enabled)return System.currentTimeMillis();lock.lock();try{return nowLocked();}finally{lock.unlock();}}
    public static long nowNanos(){if(!enabled)return System.nanoTime();lock.lock();try{return originNanos+(frozen?freezeNanos:System.nanoTime())-originNanos-heldNanos;}finally{lock.unlock();}}
    private static long nowLocked(){return originWall+TimeUnit.NANOSECONDS.toMillis((frozen?freezeNanos:System.nanoTime())-originNanos-heldNanos);}
    public static void sleep(long millis) throws InterruptedException {
        if(!enabled){Thread.sleep(millis);return;}
        if(millis<0)throw new IllegalArgumentException("negative timeout");
        lock.lockInterruptibly();try {
            long due=nowLocked()+millis;
            while(!stopped&&nowLocked()<due) {
                if(frozen)changed.await();else changed.await(Math.max(1,due-nowLocked()),TimeUnit.MILLISECONDS);
            }
            if(stopped)throw new Stopped();
        }finally{lock.unlock();}
    }
    /** Logical timers never wait on a platform scheduler lock and never run while frozen. */
    public static void schedule(Runnable action,long delayMillis) {
        if(!enabled){CompletableFuture.delayedExecutor(Math.max(0,delayMillis),TimeUnit.MILLISECONDS).execute(action);return;}
        lock.lock();try {
            if(stopped)throw new Stopped();
            timers.add(new Timed(nowLocked()+Math.max(0,delayMillis),++timerSequence,run,action));
            if(timerWorker==null){timerWorker=Thread.ofPlatform().daemon().name("checkpoint-source-timer").start(ExecutionBarrier::timers);}
            changed.signalAll();
        }finally{lock.unlock();}
    }
    private static void timers() {
        while(true) {
            Timed next;lock.lock();try {
                while(true) {
                    if(stopped)return;
                    next=timers.peek();
                    if(closed||frozen||next==null){changed.awaitUninterruptibly();continue;}
                    long wait=next.due-nowLocked();
                    if(wait>0){try{changed.await(wait,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}continue;}
                    timers.remove();admittedQueue.put(next,true);pending++;break;
                }
            }finally{lock.unlock();}
            try(var step=enterQueued(next,"source-timer")){next.action.run();}catch(Stopped stopped){return;}
            catch(RuntimeException failure){java.util.logging.Logger.getLogger(ExecutionBarrier.class.getName()).log(java.util.logging.Level.SEVERE,"Source timer failed",failure);}
        }
    }
    /** Account already-enqueued normative notifications before the cut can become READY. */
    public static void dispatch(Executor executor,Runnable action) {
        if(!enabled){executor.execute(action);return;}
        lock.lock();try {
            if(stopped)throw new Stopped();
            if(depth.get()==0)throw new IllegalStateException("CHECKPOINT_NOTIFICATION_OUTSIDE_SOURCE_STEP");
            pending++;
        }finally{lock.unlock();}
        try {executor.execute(()->{
            lock.lock();try {
                pending--;if(stopped){changed.signalAll();return;}
                if(depth.get()!=0)throw new IllegalStateException("CHECKPOINT_NOTIFICATION_REENTRANT");
                depth.set(1);active.put(Thread.currentThread().threadId(),"source-notification");changed.signalAll();
            }finally{lock.unlock();}
            try{action.run();}finally{leave("source-notification");}
        });}catch(RuntimeException failed){lock.lock();try{pending--;changed.signalAll();}finally{lock.unlock();}throw failed;}
    }
    public static void stop(){lock.lock();try{stopped=true;closed=true;timers.clear();pending-=admittedQueue.size();admittedQueue.clear();changed.signalAll();}finally{lock.unlock();}}
    private static void requireEnabled(){if(!enabled)throw new IllegalStateException("CHECKPOINT_RUN_NOT_ENABLED");}
}

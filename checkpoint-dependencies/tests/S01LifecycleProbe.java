import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.jacamo.checkpoint.runtime.*;

/** Fresh-JVM negative and lifecycle probes for the source barrier. */
public class S01LifecycleProbe {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        String kind=args[0];Path root=Path.of(args[1]);Files.createDirectories(root);
        if(kind.equals("admission")) {
            Path safe=root.resolve("safe.java");Files.writeString(safe,"class Plain { int value; void change(){value++;} }");
            check(SourceAdmission.inspect(root,List.of(safe)).supported(),"plain source admitted");
            for(String source:List.of("new Thread(() -> mutate()).start();","System.currentTimeMillis();",".at(\"now + 1s\",{+!change})")) {
                Path unsafe=root.resolve(source.startsWith(".at")?"unsafe.asl":"unsafe.java");Files.writeString(unsafe,source);
                var report=SourceAdmission.inspect(root,List.of(safe,unsafe));check(!report.supported(),"unsupported clock/producer rejected");
                try{report.requireSupported();throw new AssertionError("strict preflight accepted unsupported source");}catch(IllegalArgumentException expected){ }
            }
        } else {
            ExecutionBarrier.enable("probe-"+kind);
            if(kind.equals("notifications")) {
                var executor=Executors.newSingleThreadExecutor();var began=new CountDownLatch(1);var finish=new CountDownLatch(1);
                try(var step=ExecutionBarrier.enter("source-parent")) {
                    ExecutionBarrier.dispatch(executor,()->{began.countDown();try{finish.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
                }
                check(began.await(1,TimeUnit.SECONDS),"admitted notification runs");long epoch=ExecutionBarrier.closeAdmission();
                try{ExecutionBarrier.awaitFrozen(epoch,Duration.ofMillis(30));throw new AssertionError("notification escaped drain");}catch(IllegalStateException timeout){ }
                check(ExecutionBarrier.proof().admissionClosed(),"timeout/disconnect without decision cannot release");
                finish.countDown();var frozen=ExecutionBarrier.awaitFrozen(epoch,Duration.ofSeconds(1));
                check(frozen.pending()==0&&frozen.active().isEmpty(),"notification ledger drains exactly");
                var timer=new AtomicBoolean();ExecutionBarrier.schedule(()->timer.set(true),30);Thread.sleep(80);check(!timer.get(),"timer callback blocked by frozen clock");
                ExecutionBarrier.release(epoch);Thread.sleep(80);check(timer.get(),"timer resumes in source time");
                executor.shutdown();check(executor.awaitTermination(1,TimeUnit.SECONDS),"notification executor cleanup");
            } else if(kind.equals("queue")) {
                Object parent=new Object(),child=new Object();ExecutionBarrier.queued(parent);long epoch=ExecutionBarrier.closeAdmission();
                try{ExecutionBarrier.awaitFrozen(epoch,Duration.ofMillis(20));throw new AssertionError("admitted queue escaped drain");}catch(IllegalStateException expected){ }
                try(var step=ExecutionBarrier.enterQueued(parent,"admitted-parent")){ExecutionBarrier.queued(child);}
                check(ExecutionBarrier.proof().pending()==1,"causal child remains admitted after closure");
                try(var step=ExecutionBarrier.enterQueued(child,"causal-child")){ }
                var frozen=ExecutionBarrier.awaitFrozen(epoch,Duration.ofSeconds(1));check(frozen.pending()==0&&frozen.active().isEmpty(),"admitted causal queue completely drained");
                ExecutionBarrier.release(epoch);
            } else if(kind.equals("stop")) {
                long epoch=ExecutionBarrier.closeAdmission();ExecutionBarrier.awaitFrozen(epoch,Duration.ofSeconds(1));
                var stopped=new AtomicBoolean();var waiter=new Thread(()->{
                    try(var step=ExecutionBarrier.enter("new-domain-step")){throw new AssertionError("held admission entered");}
                    catch(ExecutionBarrier.Stopped expected){stopped.set(true);}
                });waiter.start();Thread.sleep(40);check(waiter.isAlive(),"new admission waits");
                ExecutionBarrier.stop();waiter.join(1000);check(!waiter.isAlive()&&stopped.get(),"Stop unblocks admission by cancellation");
                try{ExecutionBarrier.release(epoch);throw new AssertionError("Stop became release");}catch(IllegalStateException expected){ }
                check(ExecutionBarrier.proof().active().isEmpty(),"no leaked active participants");
            } else throw new IllegalArgumentException(kind);
            ExecutionBarrier.stop();
        }
        Files.writeString(root.resolve(kind+"-pass.txt"),"PASS "+kind+"\n");System.out.println("S01_LIFECYCLE_PASS="+kind);
    }
}

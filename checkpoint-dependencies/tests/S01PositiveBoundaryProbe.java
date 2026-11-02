import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import jason.architecture.AgArch;
import jason.infra.local.*;
import jason.mas2j.ClassParameters;
import jason.runtime.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;

/** Isolated component runtime probe, not an authored case or a USE verdict. */
public class S01PositiveBoundaryProbe {
    static void check(boolean test,String message){if(!test)throw new AssertionError(message);}
    static void until(java.util.function.BooleanSupplier condition,String message) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(5);
        check(condition.getAsBoolean(),message);
    }
    public static class Runner extends RunLocalMAS {void current(){runner=this;}}
    public static class CycleProbe extends AgArch {
        final AtomicLong cycles=new AtomicLong();
        @Override public void reasoningCycleFinished(){cycles.incrementAndGet();super.reasoningCycleFinished();}
    }
    public static class DomainArtifact extends Artifact {
        static volatile DomainArtifact instance;
        static final CountDownLatch timerEntered=new CountDownLatch(1),inFlight=new CountDownLatch(1),settle=new CountDownLatch(1);
        boolean ready;
        void init(){instance=this;defineObsProperty("timer",0);defineObsProperty("external",0);defineObsProperty("drained",0);defineObsProperty("session",0);defineObsProperty("guarded",0);}
        @OPERATION void arm(){execInternalOp("timer");}
        @INTERNAL_OPERATION void timer(){timerEntered.countDown();await_time(2000);updateObsProperty("timer",1);}
        @OPERATION void drain() throws InterruptedException {inFlight.countDown();settle.await();updateObsProperty("drained",1);}
        @OPERATION void external(){updateObsProperty("external",1);}
        @GUARD boolean ready(){return ready;}
        @OPERATION(guard="ready") void guarded(){updateObsProperty("guarded",1);}
        @OPERATION void makeReady(){ready=true;}
        void externalSession(){beginExtSession();try{updateObsProperty("session",1);}finally{endExtSession();}}
    }
    public static class DeadlineBoard extends ora4mas.nopl.NormativeBoard {
        static volatile DeadlineBoard instance;
        @Override public void init(){
            jason.util.Config.get().setProperty(jason.util.Config.START_WEB_OI,"false");
            super.init();instance=this;getNormativeEngine().setUpdateInterval(10);
        }
    }
    static long cycles(LocalAgArch agent){
        for(AgArch arch=agent.getFirstAgArch();arch!=null;arch=arch.getNextAgArch())if(arch instanceof CycleProbe probe)return probe.cycles.get();
        throw new AssertionError("missing cycle probe");
    }
    static LocalAgArch agent(Runner runner,LocalEnvironment env,Path asl,String name)throws Exception {
        var agent=new LocalAgArch();agent.setAgName(name);agent.setMASRunner(runner);
        agent.createArchs(List.of(CycleProbe.class.getName()),"jason.asSemantics.Agent",new ClassParameters("jason.bb.DefaultBeliefBase"),asl.toString(),new Settings());
        agent.setEnvInfraTier(env);runner.addAg(agent);agent.setThread(new Thread(agent));agent.startThread();return agent;
    }
    static int property(Workspace workspace,String field)throws Exception {
        return workspace.getController().getArtifactInfo("domain").getObsProperties().stream().filter(p->p.getName().equals(field)).findFirst().orElseThrow().intValue();
    }
    public static void main(String[] args){
        try{run(Path.of(args[0]));System.out.println("S01_POSITIVE_BOUNDARY_PROBE_PASS");System.exit(0);}
        catch(Throwable failed){failed.printStackTrace();System.exit(1);}
    }
    static void run(Path output)throws Exception {
        Files.createDirectories(output);
        check("1.0.0".equals(Artifact.checkpointHookVersion()),"patched Artifact hook");
        check("1.0.0".equals(npl.StateTransitionsThread.checkpointHookVersion()),"patched NPL updater hook");
        check("1.0.0".equals(LocalAgArch.checkpointHookVersion()),"patched local cycle hook");
        ExecutionBarrier.enable("source-boundary-probe");
        jason.util.Config.get().setProperty(jason.util.Config.START_WEB_MI,"false");
        var runner=new Runner();runner.current();RuntimeServicesFactory.set(new LocalRuntimeServices(runner));
        var environment=new LocalEnvironment(new ClassParameters("jason.environment.Environment"),runner);
        Path asl=output.resolve("clock.asl");Files.writeString(asl,"!timed.\n+!timed <- .wait(2000); +timer_done.\n");
        var agents=new ArrayList<LocalAgArch>();var clients=Executors.newFixedThreadPool(3);
        var cartago=CartagoEnvironment.getInstance();cartago.init();
        try {
            var context=new CartagoBasicContext("component-observer");var workspace=cartago.getRootWSP().getWorkspace();
            var artifact=context.makeArtifact(context.getJoinedWspId("main"),"domain",DomainArtifact.class.getName());
            var board=context.makeArtifact(context.getJoinedWspId("main"),"norms",DeadlineBoard.class.getName());
            context.doAction(board,new Op("load","scope probe { norm due: start -> obligation(worker,true,missing,`now`+`2 seconds`). }"));
            context.doAction(board,new Op("addFact","start"));
            until(()->!DeadlineBoard.instance.getNormativeEngine().getActive().isEmpty(),"real obligation activation");
            agents.add(agent(runner,environment,asl,"workerOne"));agents.add(agent(runner,environment,asl,"workerTwo"));
            until(()->agents.stream().allMatch(a->cycles(a)>0),"real agent cycles started");
            context.doAction(artifact,new Op("arm"));check(DomainArtifact.timerEntered.await(2,TimeUnit.SECONDS),"internal timer armed");
            var guardThread=new Thread(()->{try{context.doAction(artifact,new Op("guarded"));}catch(Exception e){throw new RuntimeException(e);}},"component-guard-waiter");guardThread.start();
            var drain=clients.submit(()->{try{context.doAction(artifact,new Op("drain"));}catch(Exception e){throw new RuntimeException(e);}});
            check(DomainArtifact.inFlight.await(2,TimeUnit.SECONDS),"operation already running");
            long requested=ExecutionBarrier.proof().sequence();long epoch=ExecutionBarrier.closeAdmission();
            Object queued=new Object();
            // Work requested by an in-flight step after closure is part of its
            // causal drain, not a new unrelated domain admission.
            try{ExecutionBarrier.awaitFrozen(epoch,Duration.ofMillis(30));throw new AssertionError("in-flight timeout manufactured a cut");}
            catch(IllegalStateException timeout){check(timeout.getMessage().startsWith("CHECKPOINT_DRAIN_TIMEOUT"),"drain timeout diagnostic");}
            check(ExecutionBarrier.proof().admissionClosed(),"timeout retains admission hold");
            try{ExecutionBarrier.release(epoch);throw new AssertionError("unfrozen release accepted");}catch(IllegalStateException expected){ }
            var freezing=clients.submit(()->ExecutionBarrier.awaitFrozen(epoch,Duration.ofSeconds(4)));
            Thread.sleep(100);check(!freezing.isDone(),"READY cannot precede in-flight operation completion");
            DomainArtifact.settle.countDown();drain.get(2,TimeUnit.SECONDS);
            var frozen=freezing.get(3,TimeUnit.SECONDS);
            check(frozen.active().isEmpty()&&frozen.pending()==0&&frozen.clockFrozen(),"complete barrier proof");
            check(frozen.sequence()>requested,"requested and drained boundaries differ");
            check(property(workspace,"drained")==1,"actual cut includes the drained operation");
            var counts=agents.stream().map(S01PositiveBoundaryProbe::cycles).toList();long now=ExecutionBarrier.nowMillis();
            var external=clients.submit(()->{try{context.doAction(artifact,new Op("external"));}catch(Exception e){throw new RuntimeException(e);}});
            var externalSession=clients.submit(()->DomainArtifact.instance.externalSession());
            Thread.sleep(2500);
            check(now==ExecutionBarrier.nowMillis(),"source logical clock remains exactly frozen");
            check(counts.equals(agents.stream().map(S01PositiveBoundaryProbe::cycles).toList()),"both shared-MAS agent cycles remain held");
            check(property(workspace,"timer")==0&&property(workspace,"external")==0&&property(workspace,"session")==0,"no internal timer, new operation or external-session mutation while held");
            check(property(workspace,"guarded")==0&&guardThread.isAlive(),"guard wait yields at a safe boundary without blocking the cut");
            check(DeadlineBoard.instance.getNormativeEngine().getUnFulfilled().isEmpty(),"actual NPL deadline did not consume wall hold time");
            check(!external.isDone()&&!externalSession.isDone(),"new domain admission stays held");
            check(frozen.sequence()==ExecutionBarrier.proof().sequence(),"no admitted transition after frozen cut");
            try{ExecutionBarrier.release(epoch-1);throw new AssertionError("stale release accepted");}catch(IllegalStateException expected){ }
            ExecutionBarrier.release(epoch);external.get(2,TimeUnit.SECONDS);externalSession.get(2,TimeUnit.SECONDS);
            context.doAction(artifact,new Op("makeReady"));guardThread.join(2000);check(!guardThread.isAlive()&&property(workspace,"guarded")==1,"guard resumes outside admission locks");
            check(property(workspace,"external")==1&&property(workspace,"session")==1,"exact release resumes queued work");
            check(DeadlineBoard.instance.getNormativeEngine().getUnFulfilled().isEmpty(),"deadline still has its remaining source time after release");
            until(()->!DeadlineBoard.instance.getNormativeEngine().getUnFulfilled().isEmpty(),"real NPL deadline eventually expires in source time");
            until(()->{try{return property(workspace,"timer")==1;}catch(Exception e){throw new RuntimeException(e);}},"internal timer resumes");
            until(()->agents.stream().allMatch(a->a.getTS().getAg().getBB().contains(jason.asSyntax.ASSyntax.createLiteral("timer_done"))!=null),"actual Jason .wait resumes after remaining source time");
            Files.writeString(output.resolve("positive-proof.txt"),"requestedSequence="+requested+"\nfrozen="+frozen+"\nwallHoldMillis=2500\nheldCycles="+counts+"\nactualNplDeadlineFrozen=true\nactualJasonWaitFrozen=true\nnewOperationsAndSessionsHeld=true\n");
        }finally {
            DomainArtifact.settle.countDown();for(var agent:agents)agent.stopAg();
            for(var agent:agents)agent.getThread().join(1000);
            if(DeadlineBoard.instance!=null)DeadlineBoard.instance.getNormativeEngine().stop();
            clients.shutdownNow();environment.stop();cartago.shutdown();ExecutionBarrier.stop();
        }
    }
}

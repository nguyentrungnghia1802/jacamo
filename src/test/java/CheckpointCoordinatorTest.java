import static org.junit.Assert.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import jacamo.checkpoint.VerifiedCheckpointCoordinator;
import jacamo.checkpoint.VerifiedCheckpointCoordinator.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;
import org.junit.Test;

/** Synthetic protocol/ledger tests; these do not claim native USE truth. */
public class CheckpointCoordinatorTest {
    static final String POLICY="a".repeat(64),RESULT="b".repeat(64),USE="c".repeat(64);
    static final Owner OWNER=new Owner("session","run",1,"revision");
    static void ready(VerifiedCheckpointCoordinator coordinator)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while((coordinator.view().request()==null||coordinator.view().request().cut()==null)&&System.nanoTime()<end)Thread.sleep(2);
        assertNotNull(coordinator.view().request().cut());
    }
    static Decision decision(Request request,String id,Action action,Map<String,Check> checks) {
        return new Decision(OWNER,request.token(),request.sequence(),request.barrierEpoch(),POLICY,request.cut().hash(),USE,RESULT,request.executionScope(),id,action,checks,"native-test-evidence");
    }
    static final Map<String,Check> PASS=Map.of("required",new Check(Truth.PASS,Applicability.APPLICABLE,""),"future-root",new Check(Truth.PENDING,Applicability.FUTURE,"future model-dependent goal"));
    @Test public void exactCutApprovalReviewIdempotencyUnknownTimeoutDisconnectPersistenceAndDrift()throws Exception {
        ExecutionBarrier.enable("coordinator-unit");
        var events=new CopyOnWriteArrayList<Map<String,Object>>();var persistFailure=new AtomicBoolean();var drift=new AtomicBoolean();
        var port=new VerifiedCheckpointCoordinator.Port(){
            public SourceCut capture(ExecutionBarrier.Proof proof)throws Exception {
                byte[] bytes=("{\"sourceSequence\":"+proof.sequence()+"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                return new SourceCut(hash,bytes,Map.of("source",proof.sequence()));
            }
            public boolean unchanged(Request request){return !drift.get();}
            public void append(Map<String,Object> event){if(persistFailure.getAndSet(false))throw new IllegalStateException("disk unavailable");events.add(event);}
        };
        var coordinator=new VerifiedCheckpointCoordinator(OWNER,port,Duration.ofMillis(300));
        var boundary=new Boundary(POLICY,"task-post","occurrence",Set.of("execution"),Set.of("required"),Map.of("model","d".repeat(64)));
        try {
            assertTrue(coordinator.trigger(boundary));ready(coordinator);Request first=coordinator.view().request();
            var approved=decision(first,"approve-first",Action.APPROVE,PASS);var receipt=coordinator.decide(approved);assertTrue(receipt.released());
            assertEquals(receipt,coordinator.decide(approved));
            assertTrue(coordinator.trigger(boundary));ready(coordinator);Request failing=coordinator.view().request();
            assertEquals(receipt,coordinator.decide(approved));assertEquals(failing.token(),coordinator.view().request().token());assertTrue(ExecutionBarrier.proof().admissionClosed());
            var failed=Map.of("required",new Check(Truth.FAIL,Applicability.APPLICABLE,"unchanged original failure"));
            assertFalse(coordinator.decide(decision(failing,"failure",Action.HOLD_FAILURE,failed)).released());assertEquals(State.HELD_FAILURE,coordinator.view().state());
            var wrong=new Decision(new Owner("session","other-run",2,"revision"),failing.token(),failing.sequence(),failing.barrierEpoch(),POLICY,failing.cut().hash(),USE,RESULT,failing.executionScope(),"foreign",Action.APPROVE,PASS,"");
            assertThrows(IllegalArgumentException.class,()->coordinator.decide(wrong));
            assertThrows(IllegalArgumentException.class,()->coordinator.decide(decision(failing,"approve-first",Action.APPROVE,PASS)));
            assertTrue(coordinator.decide(decision(failing,"review-failure",Action.ACKNOWLEDGE_AND_CONTINUE,failed)).released());
            assertEquals(RESULT,coordinator.view().resultHash());assertEquals(failed,coordinator.view().checks());
            for(Truth truth:List.of(Truth.UNKNOWN,Truth.ERROR,Truth.SKIPPED,Truth.PENDING)) {
                coordinator.trigger(boundary);ready(coordinator);Request request=coordinator.view().request();
                var unknown=Map.of("required",new Check(truth,Applicability.APPLICABLE,"required evidence unavailable"));
                assertFalse(coordinator.decide(decision(request,"unknown-"+truth,Action.APPROVE,unknown)).released());
                assertEquals(State.HELD_UNVERIFIED,coordinator.view().state());
                assertTrue(coordinator.decide(decision(request,"review-"+truth,Action.ACKNOWLEDGE_AND_CONTINUE,unknown)).released());
            }
            coordinator.trigger(boundary);ready(coordinator);Request disconnected=coordinator.view().request();coordinator.disconnected();
            assertFalse(coordinator.decide(decision(disconnected,"late-pass",Action.APPROVE,PASS)).released());
            assertTrue(coordinator.decide(decision(disconnected,"review-disconnect",Action.ACKNOWLEDGE_AND_CONTINUE,PASS)).released());
            coordinator.trigger(boundary);ready(coordinator);Request timeout=coordinator.view().request();Thread.sleep(380);
            assertEquals(State.HELD_UNVERIFIED,coordinator.view().state());assertFalse(coordinator.decide(decision(timeout,"timeout-pass",Action.APPROVE,PASS)).released());
            assertTrue(coordinator.decide(decision(timeout,"review-timeout",Action.ACKNOWLEDGE_AND_CONTINUE,PASS)).released());
            coordinator.trigger(boundary);ready(coordinator);Request persistence=coordinator.view().request();persistFailure.set(true);
            assertThrows(IllegalStateException.class,()->coordinator.decide(decision(persistence,"disk-pass",Action.APPROVE,PASS)));
            assertTrue(ExecutionBarrier.proof().admissionClosed());assertEquals(State.HELD_UNVERIFIED,coordinator.view().state());
            assertTrue(coordinator.decide(decision(persistence,"review-disk",Action.ACKNOWLEDGE_AND_CONTINUE,PASS)).released());
            coordinator.trigger(boundary);ready(coordinator);Request drifted=coordinator.view().request();drift.set(true);
            assertThrows(IllegalStateException.class,()->coordinator.decide(decision(drifted,"drift-pass",Action.APPROVE,PASS)));
            assertTrue(ExecutionBarrier.proof().admissionClosed());
            assertTrue(events.stream().anyMatch(e->e.get("kind").equals("DECISION")&&e.toString().contains("unchanged original failure")));
            assertTrue(events.stream().anyMatch(e->e.get("kind").equals("ACKNOWLEDGED")));
            assertTrue(events.stream().anyMatch(e->e.get("kind").equals("RELEASE_RECEIPT")));
        }finally{coordinator.close();}
        assertEquals(State.STOPPED,coordinator.view().state());assertTrue(ExecutionBarrier.proof().admissionClosed());
    }
}

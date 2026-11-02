package jacamo.checkpoint;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;

/** Producer-owned exact-cut hold. Transport authentication belongs to its adapter. */
public final class VerifiedCheckpointCoordinator implements AutoCloseable {
    public static final String VERSION="1.0.0";
    public enum State { RUNNING, QUIESCING, VERIFYING, HELD_FAILURE, HELD_UNVERIFIED, STOPPED }
    public enum Action { APPROVE, HOLD_FAILURE, HOLD_UNVERIFIED, ACKNOWLEDGE_AND_CONTINUE }
    public enum Truth { PASS, FAIL, UNKNOWN, ERROR, SKIPPED, PENDING }
    public enum Applicability { APPLICABLE, FUTURE, NOT_APPLICABLE }
    public record Owner(String session,String run,long generation,String revision) {
        public Owner {require(session);require(run);require(revision);if(generation<1)throw new IllegalArgumentException("CHECKPOINT_GENERATION");}
    }
    public record Boundary(String policyHash,String checkpointId,String sourceOccurrence,Set<String> executionScope,
                           Set<String> requiredChecks,Map<String,String> inputHashes) {
        public Boundary {
            digest(policyHash);require(checkpointId);require(sourceOccurrence);
            executionScope=Set.copyOf(executionScope);requiredChecks=Set.copyOf(requiredChecks);inputHashes=Map.copyOf(inputHashes);
            if(executionScope.isEmpty()||requiredChecks.isEmpty()||inputHashes.isEmpty())throw new IllegalArgumentException("CHECKPOINT_SCOPE_OR_REQUIREMENTS_MISSING");
            inputHashes.values().forEach(VerifiedCheckpointCoordinator::digest);
        }
    }
    public record SourceCut(String hash,byte[] snapshot,Map<String,Long> watermarks) {
        public SourceCut {
            digest(hash);snapshot=snapshot.clone();watermarks=Map.copyOf(watermarks);if(snapshot.length==0||watermarks.isEmpty())throw new IllegalArgumentException("CHECKPOINT_CUT_EMPTY");
            try{if(!hash.equals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(snapshot))))throw new IllegalArgumentException("CHECKPOINT_SNAPSHOT_HASH_MISMATCH");}
            catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        }
        @Override public byte[] snapshot(){return snapshot.clone();}
    }
    public record Request(Owner owner,String token,long sequence,long barrierEpoch,Boundary boundary,
                          List<Boundary> drainedBoundaries,long requestedSequence,ExecutionBarrier.Proof frozen,SourceCut cut) {
        public Request {drainedBoundaries=List.copyOf(drainedBoundaries);}
        public Set<String> requiredChecks(){var result=new TreeSet<String>(boundary.requiredChecks());drainedBoundaries.forEach(b->result.addAll(b.requiredChecks()));return Set.copyOf(result);}
        public Set<String> executionScope(){var result=new TreeSet<String>(boundary.executionScope());drainedBoundaries.forEach(b->result.addAll(b.executionScope()));return Set.copyOf(result);}
    }
    public record Check(Truth truth,Applicability applicability,String diagnostic) {
        public Check {Objects.requireNonNull(truth);Objects.requireNonNull(applicability);Objects.requireNonNull(diagnostic);}
    }
    public record Decision(Owner owner,String token,long sequence,long barrierEpoch,String policyHash,String producerHash,
                           String acceptedUseCutHash,String resultHash,Set<String> executionScope,String commandId,Action action,
                           Map<String,Check> checks,String diagnostic) {
        public Decision {
            Objects.requireNonNull(owner);require(token);digest(policyHash);digest(producerHash);digest(acceptedUseCutHash);digest(resultHash);
            executionScope=Set.copyOf(executionScope);require(commandId);Objects.requireNonNull(action);checks=Map.copyOf(checks);Objects.requireNonNull(diagnostic);
        }
    }
    public record Receipt(String commandId,String token,boolean released,State state,String originalResultHash,
                          String acceptedUseCutHash,String diagnostic,long barrierEpoch) { }
    public record View(State state,Request request,String reason,String resultHash,String acceptedUseCutHash,
                       Map<String,Check> checks,long ledgerSequence) {public View{checks=Map.copyOf(checks);}}
    public interface Port {
        SourceCut capture(ExecutionBarrier.Proof proof) throws Exception;
        boolean unchanged(Request request);
        void append(Map<String,Object> event);
    }
    private record Command(Decision decision,Receipt receipt) { }
    private final Owner owner;private final Port port;private final Duration verificationTimeout;
    private final ExecutorService freezing=Executors.newSingleThreadExecutor(r->Thread.ofPlatform().daemon().name("jacamo-checkpoint-cut").unstarted(r));
    private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor(r->Thread.ofPlatform().daemon().name("jacamo-checkpoint-control-timeout").unstarted(r));
    private final Map<String,Command> commands=new LinkedHashMap<>();
    private final List<Boundary> causes=new ArrayList<>();
    private State state=State.RUNNING;private Request active;private String reason="",resultHash="",useHash="";
    private Map<String,Check> checks=Map.of();private long sequence,ledgerSequence,deadlineNanos;private boolean latched,closed;
    public VerifiedCheckpointCoordinator(Owner owner,Port port,Duration verificationTimeout) {
        this.owner=Objects.requireNonNull(owner);this.port=Objects.requireNonNull(port);this.verificationTimeout=Objects.requireNonNull(verificationTimeout);
        if(verificationTimeout.toMillis()<100)throw new IllegalArgumentException("CHECKPOINT_TIMEOUT_TOO_SHORT");
        watchdog.scheduleWithFixedDelay(this::watch,50,50,TimeUnit.MILLISECONDS);
    }
    /** Called synchronously at a selected source event; never waits for USE or a cut. */
    public synchronized boolean trigger(Boundary boundary) {
        if(closed)throw new IllegalStateException("CHECKPOINT_OWNER_CLOSED");
        if(active!=null) {
            if(active.cut()==null&&active.boundary().policyHash().equals(boundary.policyHash()))causes.add(boundary);
            return false;
        }
        if(state!=State.RUNNING)throw new IllegalStateException("CHECKPOINT_OWNER_NOT_RUNNING");
        long requested=ExecutionBarrier.proof().sequence();long epoch=ExecutionBarrier.closeAdmission();
        active=new Request(owner,UUID.randomUUID().toString(),++sequence,epoch,boundary,List.of(),requested,null,null);
        state=State.QUIESCING;reason="DRAINING_ADMITTED_SOURCE_WORK";resultHash="";useHash="";checks=Map.of();latched=false;causes.clear();
        Request selected=active;freezing.execute(()->freeze(selected));return true;
    }
    private void freeze(Request selected) {
        try {
            synchronized(this){record("REQUEST",selected,Map.of("requestedSequence",selected.requestedSequence()));}
            ExecutionBarrier.Proof proof;
            while(true) {
                try{proof=ExecutionBarrier.awaitFrozen(selected.barrierEpoch(),Duration.ofSeconds(5));break;}
                catch(IllegalStateException timeout) {
                    if(!timeout.getMessage().startsWith("CHECKPOINT_DRAIN_TIMEOUT"))throw timeout;
                    synchronized(this){if(closed)return;latched=true;state=State.HELD_UNVERIFIED;reason=timeout.getMessage();record("DRAIN_TIMEOUT",active,Map.of("diagnostic",reason));}
                }
            }
            SourceCut cut=port.capture(proof);
            synchronized(this) {
                if(closed||active!=selected)return;
                var drained=List.copyOf(causes);
                active=new Request(owner,selected.token(),selected.sequence(),selected.barrierEpoch(),selected.boundary(),drained,selected.requestedSequence(),proof,cut);
                record("FROZEN",active,Map.of("snapshotBase64",Base64.getEncoder().encodeToString(cut.snapshot()),"producerHash",cut.hash(),"watermarks",cut.watermarks(),"frozenSequence",proof.sequence(),"clockMillis",proof.sourceMillis()));
                state=latched?State.HELD_UNVERIFIED:State.VERIFYING;reason=latched?reason:"WAITING_FOR_EXACT_CUT_VERIFICATION";
                deadlineNanos=System.nanoTime()+verificationTimeout.toNanos();
            }
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();hold("CHECKPOINT_CUT_INTERRUPTED");}
        catch(Exception failed){hold("CHECKPOINT_CUT_UNAVAILABLE:"+failed.getMessage());}
    }
    public synchronized View view(){return new View(state,active,reason,resultHash,useHash,checks,ledgerSequence);}
    public Owner owner(){return owner;}
    public synchronized Receipt decide(Decision decision) {
        var previous=commands.get(decision.commandId());
        if(previous!=null) {
            if(!previous.decision().equals(decision))throw new IllegalArgumentException("CHECKPOINT_COMMAND_ID_COLLISION");
            return previous.receipt();
        }
        Request request=active;
        if(closed||request==null||request.cut()==null||request.frozen()==null)throw new IllegalStateException("CHECKPOINT_VALID_FROZEN_CUT_REQUIRED");
        if(!owner.equals(decision.owner())||!request.token().equals(decision.token())||request.sequence()!=decision.sequence()
                ||request.barrierEpoch()!=decision.barrierEpoch()||!request.boundary().policyHash().equals(decision.policyHash())
                ||!request.cut().hash().equals(decision.producerHash())||!request.executionScope().equals(decision.executionScope()))
            throw new IllegalArgumentException("CHECKPOINT_DECISION_IDENTITY_STALE_OR_FOREIGN");
        if(commands.size()>=16384){hold("CHECKPOINT_COMMAND_LEDGER_CAPACITY");throw new IllegalStateException(reason);}
        var proof=ExecutionBarrier.proof();
        if(!proof.admissionClosed()||!proof.clockFrozen()||proof.epoch()!=request.barrierEpoch()||proof.sequence()!=request.frozen().sequence()
                ||!proof.active().isEmpty()||proof.pending()!=0||!port.unchanged(request)) {
            hold("CHECKPOINT_CUT_DRIFTED");throw new IllegalStateException(reason);
        }
        boolean acknowledge=decision.action()==Action.ACKNOWLEDGE_AND_CONTINUE;
        if(acknowledge&&(state!=State.HELD_FAILURE&&state!=State.HELD_UNVERIFIED
                ||resultHash.isBlank()||!resultHash.equals(decision.resultHash())||!useHash.equals(decision.acceptedUseCutHash())||!checks.equals(decision.checks())))
            throw new IllegalArgumentException("CHECKPOINT_REVIEW_NOT_BOUND_TO_HELD_RESULT");
        if(!acknowledge&&latched&&!resultHash.isBlank()) {
            // Reconnect/reverification cannot overwrite the already held native
            // failure or upgrade it to PASS. Only exact reviewed release can move it.
            var retained=new Receipt(decision.commandId(),request.token(),false,state,resultHash,useHash,"CHECKPOINT_ORIGINAL_HELD_RESULT_RETAINED:"+reason,request.barrierEpoch());
            commands.put(decision.commandId(),new Command(decision,retained));return retained;
        }
        boolean failed=decision.checks().values().stream().anyMatch(c->c.applicability()==Applicability.APPLICABLE&&c.truth()==Truth.FAIL);
        boolean complete=request.requiredChecks().stream().allMatch(id->{var c=decision.checks().get(id);return c!=null&&c.applicability()==Applicability.APPLICABLE&&c.truth()==Truth.PASS;});
        if(state==State.VERIFYING&&System.nanoTime()>=deadlineNanos){latched=true;state=State.HELD_UNVERIFIED;reason="CHECKPOINT_VERIFICATION_TIMEOUT";}
        boolean release=acknowledge||decision.action()==Action.APPROVE&&complete&&!failed&&!latched&&state==State.VERIFYING;
        // Preserve native truth and result identities on acknowledgement.
        if(!acknowledge){resultHash=decision.resultHash();useHash=decision.acceptedUseCutHash();checks=decision.checks();}
        record(acknowledge?"ACKNOWLEDGED":"DECISION",request,decisionEvidence(decision));
        if(release) {
            record("RELEASE_INTENT",request,Map.of("commandId",decision.commandId(),"reviewOverride",acknowledge,"resultHash",resultHash));
            ExecutionBarrier.release(request.barrierEpoch());
            state=State.RUNNING;reason=acknowledge?"REVIEWED_CHECKPOINT_CONTINUED":"QUALIFIED_EXACT_CUT_APPROVED";
            try{record("RELEASE_RECEIPT",request,Map.of("commandId",decision.commandId(),"resultHash",resultHash,"reviewOverride",acknowledge));}
            catch(RuntimeException persistenceFailure){
                // The authorized release happened; never hide a missing receipt.
                // Close admission again and require a new cut before any review.
                ExecutionBarrier.closeAdmission();state=State.HELD_UNVERIFIED;reason="CHECKPOINT_RELEASE_RECEIPT_PERSISTENCE_FAILED";latched=true;
                active=null;throw persistenceFailure;
            }
            var receipt=new Receipt(decision.commandId(),request.token(),true,state,resultHash,useHash,reason,request.barrierEpoch());
            commands.put(decision.commandId(),new Command(decision,receipt));active=null;return receipt;
        }
        latched=true;
        if(failed){state=State.HELD_FAILURE;reason="APPLICABLE_CHECK_FAILED:"+decision.diagnostic();}
        else {state=State.HELD_UNVERIFIED;if(reason.equals("WAITING_FOR_EXACT_CUT_VERIFICATION"))reason="CHECKPOINT_UNVERIFIED:"+decision.diagnostic();}
        var receipt=new Receipt(decision.commandId(),request.token(),false,state,resultHash,useHash,reason,request.barrierEpoch());
        commands.put(decision.commandId(),new Command(decision,receipt));return receipt;
    }
    private Map<String,Object> decisionEvidence(Decision decision) {
        var rows=new TreeMap<String,Object>();decision.checks().forEach((id,c)->rows.put(id,Map.of("truth",c.truth().name(),"applicability",c.applicability().name(),"diagnostic",c.diagnostic())));
        return Map.of("commandId",decision.commandId(),"action",decision.action().name(),"resultHash",decision.resultHash(),"acceptedUseCutHash",decision.acceptedUseCutHash(),"checks",rows,"diagnostic",decision.diagnostic());
    }
    public void disconnected(){hold("CHECKPOINT_OBSERVER_DISCONNECTED");}
    private synchronized void hold(String diagnostic) {
        if(closed||active==null)return;
        latched=true;state=State.HELD_UNVERIFIED;reason=diagnostic;
        try{record("HOLD_UNVERIFIED",active,Map.of("diagnostic",diagnostic));}catch(RuntimeException unavailable){reason=diagnostic+";CHECKPOINT_LEDGER_UNAVAILABLE";}
    }
    private void watch(){
        synchronized(this){if(!closed&&state==State.VERIFYING&&System.nanoTime()>=deadlineNanos)hold("CHECKPOINT_VERIFICATION_TIMEOUT");}
    }
    private void record(String kind,Request request,Map<String,Object> details) {
        var event=new LinkedHashMap<String,Object>();event.put("version",VERSION);event.put("kind",kind);event.put("ledgerSequence",++ledgerSequence);
        event.put("recordedAt",Instant.now().toString());event.put("session",owner.session());event.put("run",owner.run());event.put("generation",owner.generation());event.put("revision",owner.revision());
        event.put("token",request.token());event.put("sequence",request.sequence());event.put("barrierEpoch",request.barrierEpoch());event.put("policyHash",request.boundary().policyHash());event.put("checkpointId",request.boundary().checkpointId());
        event.put("sourceOccurrence",request.boundary().sourceOccurrence());event.put("executionScope",request.executionScope().stream().sorted().toList());event.put("inputHashes",request.boundary().inputHashes());event.put("details",details);
        event.put("drainedBoundaries",request.drainedBoundaries().stream().map(b->Map.of("checkpointId",b.checkpointId(),"sourceOccurrence",b.sourceOccurrence(),"executionScope",b.executionScope().stream().sorted().toList(),"requiredChecks",b.requiredChecks().stream().sorted().toList())).toList());
        try{port.append(Map.copyOf(event));}
        catch(RuntimeException failed){latched=true;state=State.HELD_UNVERIFIED;reason="CHECKPOINT_LEDGER_UNAVAILABLE";throw failed;}
    }
    @Override public synchronized void close(){
        if(closed)return;
        if(active!=null)try{record("STOP",active,Map.of("diagnostic","OWNED_RUNTIME_STOPPED","resultHash",resultHash));}catch(RuntimeException unavailable){/* Stop remains available when persistence is unavailable. */}
        closed=true;state=State.STOPPED;reason="OWNED_RUNTIME_STOPPED";watchdog.shutdownNow();freezing.shutdownNow();ExecutionBarrier.stop();
    }
    private static void require(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("CHECKPOINT_VALUE_REQUIRED");}
    private static void digest(String value){if(value==null||!value.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("CHECKPOINT_HASH_REQUIRED");}
}

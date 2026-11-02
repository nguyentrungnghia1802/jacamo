package jacamo.checkpoint;

import java.nio.file.*;
import java.util.*;
import jacamo.project.JaCaMoProject;
import org.jacamo.checkpoint.runtime.*;

/** Reject uncovered producers before createCustomPlatforms/createEnvironment. */
public final class CheckpointBoot {
    private static SourceAdmission.Report admitted;
    private static Path sourceRoot;
    private static volatile VerifiedCheckpointCoordinator coordinator;
    private CheckpointBoot(){ }
    public static boolean verified(){return "VERIFIED_CHECKPOINTS".equals(System.getProperty(CheckpointFeasibility.MODE_PROPERTY,"PASSIVE"));}
    public static synchronized void admit(JaCaMoProject project){
        if(!verified())return;
        if(project.isJade()||!project.getInstitutions().isEmpty())throw new IllegalArgumentException("CHECKPOINT_UNSUPPORTED_DISTRIBUTED_OR_INSTITUTIONAL_EXECUTION");
        if(project.getControlClass()!=null)throw new IllegalArgumentException("CHECKPOINT_UNSUPPORTED_CUSTOM_AGENT_CONTROL");
        Set<String> architectures=Set.of("jacamo.infra.JaCaMoAgArch","jaca.CAgentArch","org.jacamo.bridge.adapter.BridgeAgArch");
        for(var agent:project.getAgents()) {
            if(agent.getHost()!=null&&!agent.getHost().isBlank()||!architectures.containsAll(agent.getAgArchClasses())||agent.getBBClass()!=null&&!"jason.bb.DefaultBeliefBase".equals(agent.getBBClass().getClassName())
                    ||agent.agClass!=null&&!"jason.asSemantics.Agent".equals(agent.agClass.getClassName()))
                throw new IllegalArgumentException("CHECKPOINT_UNSUPPORTED_AGENT_PROVIDER");
        }
        Set<String> allowed=Set.of("org.jacamo.bridge.adapter.JaCaMoBridgePlatform","jacamo.platform.EnvironmentWebInspector");
        if(!allowed.containsAll(project.getCustomPlatforms()))throw new IllegalArgumentException("CHECKPOINT_UNCOVERED_CUSTOM_PLATFORM");
        if(project.getWorkspaces().stream().anyMatch(w->w.getHost()!=null&&!w.getHost().isBlank()))throw new IllegalArgumentException("CHECKPOINT_REMOTE_WORKSPACE_UNSUPPORTED");
        try {
            Path root=Path.of(System.getProperty("jacamo.checkpoint.projectRoot",project.getDirectory())).toRealPath();List<Path> files;
            try(var paths=Files.walk(root)) {
                files=paths.filter(Files::isRegularFile).filter(p->{
                    String relative=root.relativize(p).toString().replace('\\','/');
                    return !relative.startsWith("build/")&&!relative.startsWith(".git/")&&!relative.startsWith(".gradle/")
                        &&(relative.endsWith(".java")||relative.endsWith(".asl")||relative.endsWith(".jcm")||relative.endsWith(".xml"));
                }).toList();
            }
            var clocks=SourceClockWeaver.prepare(root,files);
            var report=SourceAdmission.inspect(root,files,(including,reference)->resolveInclude(project,root,including,reference),clocks.sources());report.requireSupported();
            var hashes=new TreeMap<>(report.hashes());hashes.putAll(clocks.classes());report=new SourceAdmission.Report(hashes,report.rejected());
            if(admitted!=null&&!admitted.hashes().equals(report.hashes()))throw new IllegalStateException("CHECKPOINT_ADMITTED_SOURCE_CHANGED");
            admitted=report;sourceRoot=root;
            if(!ExecutionBarrier.enabled())ExecutionBarrier.enable(System.getProperty("jacamo.execution.run",UUID.randomUUID().toString()));
        }catch(java.io.IOException failure){throw new IllegalStateException("CHECKPOINT_SOURCE_ADMISSION_UNAVAILABLE",failure);}
        catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new IllegalStateException("CHECKPOINT_SOURCE_CLOCK_ADMISSION_UNAVAILABLE",failure);}
    }
    private static Path resolveInclude(JaCaMoProject project,Path root,Path including,String reference)throws java.io.IOException {
        var candidates=new LinkedHashSet<Path>();candidates.add(including.getParent().resolve(reference).normalize());candidates.add(root.resolve(reference).normalize());
        for(String entry:project.getSourcePaths().getPaths())try {
            java.net.URI uri=entry.startsWith("file:")?java.net.URI.create(entry):null;
            Path path=uri==null?Path.of(entry):uri.isOpaque()?Path.of(uri.getSchemeSpecificPart()):Path.of(uri);
            if(!path.isAbsolute())path=root.resolve(path);candidates.add(path.resolve(reference).normalize());
        }catch(RuntimeException unsupported){/* Non-file providers require a separately admitted capability. */}
        for(Path candidate:candidates)if(Files.isRegularFile(candidate))return candidate.toRealPath();return null;
    }
    public static synchronized Map<String,String> inputHashes(){return admitted==null?Map.of():admitted.hashes();}
    public static synchronized boolean sourcesUnchanged(){
        if(admitted==null||sourceRoot==null)return false;
        try {
            for(var entry:admitted.hashes().entrySet()) {
                byte[] bytes=Files.readAllBytes(sourceRoot.resolve(entry.getKey()));
                String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                if(!hash.equals(entry.getValue()))return false;
            }
            return true;
        }catch(Exception unavailable){return false;}
    }
    public static synchronized void registerCoordinator(VerifiedCheckpointCoordinator owner){
        if(!verified()||!ExecutionBarrier.enabled()||coordinator!=null||!owner.owner().run().equals(ExecutionBarrier.proof().run()))throw new IllegalStateException("CHECKPOINT_COORDINATOR_OWNER_INVALID");
        coordinator=owner;
    }
    public static synchronized void requireReadyForReasoning(){
        if(verified()&&(coordinator==null||coordinator.view().state()==VerifiedCheckpointCoordinator.State.STOPPED))throw new IllegalStateException("CHECKPOINT_ADMITTED_COORDINATOR_REQUIRED_BEFORE_REASONING");
    }
    public static boolean cohortActive(){var current=coordinator;return verified()&&current!=null&&current.view().state()!=VerifiedCheckpointCoordinator.State.STOPPED;}
}

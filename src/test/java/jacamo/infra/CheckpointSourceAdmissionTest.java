package jacamo.infra;

import java.io.*;
import java.nio.file.*;
import jacamo.checkpoint.*;
import jacamo.project.JaCaMoProject;
import jacamo.project.parser.JaCaMoProjectParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class CheckpointSourceAdmissionTest {
    static class Tripwire extends JaCaMoLauncher {
        void project(JaCaMoProject project){this.project=project;}
        @Override void createCustomPlatforms(){throw new AssertionError("unsafe source reached platform creation");}
    }
    @Test public void uncoveredAutonomousAndClockSourcesRejectBeforeAnyPlatformCreation() throws Exception {
        Path root=Files.createTempDirectory("checkpoint-admission-");Path policy=Files.writeString(root.resolve("policy.properties"),"version=1.0\n");
        var project=new JaCaMoProjectParser(new StringReader("mas component { }")).parse(root.toString());
        String oldMode=System.getProperty(CheckpointFeasibility.MODE_PROPERTY),oldPolicy=System.getProperty("jacamo.checkpoint.policy");
        try {
            System.setProperty(CheckpointFeasibility.MODE_PROPERTY,"VERIFIED_CHECKPOINTS");System.setProperty("jacamo.checkpoint.policy",policy.toString());
            for(String body:new String[]{"class Producer {void run(){new Thread(() -> {}).start();}}","class Clock {long now(){return System.currentTimeMillis();}}"}) {
                Files.writeString(root.resolve("Producer.java"),body);var launcher=new Tripwire();launcher.project(project);
                var failure=assertThrows(IllegalArgumentException.class,launcher::create);
                assertTrue(failure.getMessage().contains("CHECKPOINT_SOURCE_UNSUPPORTED"));assertTrue(launcher.getPlatforms().isEmpty());
            }
            Files.delete(root.resolve("Producer.java"));
            for(String reference:new String[]{"cartago.tools.Clock","outside.library.TimedArtifact"}) {
                Files.writeString(root.resolve("component.jcm"),"mas component { workspace main { artifact clock: "+reference+" } }");
                var launcher=new Tripwire();launcher.project(project);
                assertThrows(IllegalArgumentException.class,launcher::create);assertTrue(launcher.getPlatforms().isEmpty());
            }
        } finally {
            if(oldMode==null)System.clearProperty(CheckpointFeasibility.MODE_PROPERTY);else System.setProperty(CheckpointFeasibility.MODE_PROPERTY,oldMode);
            if(oldPolicy==null)System.clearProperty("jacamo.checkpoint.policy");else System.setProperty("jacamo.checkpoint.policy",oldPolicy);
            Files.deleteIfExists(root.resolve("Producer.java"));Files.deleteIfExists(root.resolve("component.jcm"));Files.deleteIfExists(policy);Files.delete(root);
        }
    }
}

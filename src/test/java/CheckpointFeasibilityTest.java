import static org.junit.Assert.*;

import jacamo.checkpoint.CheckpointFeasibility;
import jacamo.checkpoint.CheckpointFeasibility.Mode;
import jacamo.infra.JaCaMoLauncher;
import org.junit.Test;

/** No MAS/case execution: prove rejection before any platform initialization. */
public class CheckpointFeasibilityTest {
    @Test public void reviewedSourceHooksArePinnedBeforeStrictScopeAdmission() throws Exception {
        assertTrue(CheckpointFeasibility.strictSupported());
        assertTrue(CheckpointFeasibility.missingHooks().isEmpty());
        CheckpointFeasibility.requireSupported(Mode.PASSIVE);
        CheckpointFeasibility.requireSupported(Mode.VERIFIED_CHECKPOINTS);
        assertTrue(jacamo.checkpoint.CheckpointBuildIdentity.complete());
    }

    @Test public void initCreateAndStartRejectBeforePlatformSideEffects() {
        String previous = System.getProperty(CheckpointFeasibility.MODE_PROPERTY);
        try {
            System.setProperty(CheckpointFeasibility.MODE_PROPERTY, Mode.VERIFIED_CHECKPOINTS.name());
            // init must fail before parsing even a missing JCM; create/start
            // must fail before dereferencing a project or starting a platform.
            var launcher = new JaCaMoLauncher();
            assertThrows(IllegalStateException.class, () -> launcher.init(new String[0]));
            assertThrows(IllegalStateException.class, launcher::create);
            assertThrows(IllegalStateException.class, launcher::start);
            System.setProperty(CheckpointFeasibility.MODE_PROPERTY, "invalid");
            assertThrows(IllegalArgumentException.class, () -> launcher.init(new String[0]));
        } finally {
            if (previous == null) System.clearProperty(CheckpointFeasibility.MODE_PROPERTY);
            else System.setProperty(CheckpointFeasibility.MODE_PROPERTY, previous);
        }
    }
}

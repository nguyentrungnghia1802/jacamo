package jacamo.checkpoint;

import java.util.List;

/**
 * S01 startup gate for the currently linked platform. This is a feasibility
 * report, not a checkpoint coordinator or a claim that cycle ACKs freeze a MAS.
 * No strict scope is admitted until the mutation and time hooks are proven.
 */
public final class CheckpointFeasibility {
    public static final String VERSION = "1.0.0";
    public static final String MODE_PROPERTY = "jacamo.execution.mode";
    public enum Mode { PASSIVE, VERIFIED_CHECKPOINTS }
    public record MissingHook(String owner, String hook) { }
    private static final List<MissingHook> MISSING = List.of(
            new MissingHook("CArtAgO", "all-operation admission, in-flight/await drain and internal/linked/external mutation barrier"),
            new MissingHook("Moise/NPL", "normative transition admission and quiescence with board mutation watermark"),
            new MissingHook("NPL", "source-owned deadline clock and scheduler using the same frozen logical time"),
            new MissingHook("Jason", "source-owned .wait/timer clock and asynchronous intention admission"));

    private CheckpointFeasibility() { }
    public static boolean strictSupported() { return CheckpointBuildIdentity.complete(); }
    public static List<MissingHook> missingHooks() { return strictSupported()?List.of():MISSING; }
    public static String diagnostic() {
        return strictSupported()?"SOURCE_BARRIER_HOOKS_PINNED;LOCAL_SOURCE_ADMISSION_REQUIRED;NOT_END_TO_END_ACCEPTANCE":"VERIFIED_CHECKPOINTS_UNSUPPORTED:S01:" + MISSING;
    }
    public static void requireSupported(Mode mode) {
        if (mode == null) throw new IllegalArgumentException("CHECKPOINT_EXECUTION_MODE_REQUIRED");
        if (mode == Mode.VERIFIED_CHECKPOINTS && !strictSupported())
            throw new IllegalStateException(diagnostic());
    }
    public static void requireConfiguredModeSupported() {
        final Mode mode;
        try { mode = Mode.valueOf(System.getProperty(MODE_PROPERTY, Mode.PASSIVE.name())); }
        catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("CHECKPOINT_EXECUTION_MODE_INVALID", invalid);
        }
        requireSupported(mode);
        if(mode==Mode.VERIFIED_CHECKPOINTS) {
            String policy=System.getProperty("jacamo.checkpoint.policy","");
            if(policy.isBlank()||!java.nio.file.Files.isRegularFile(java.nio.file.Path.of(policy),java.nio.file.LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("CHECKPOINT_VERSIONED_POLICY_REQUIRED");
        }
    }
}

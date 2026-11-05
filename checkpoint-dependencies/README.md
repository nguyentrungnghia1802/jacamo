# Supported checkpoint source overlays

The boundary is one owned, source-admitted local MAS. Jason reasoning cycles,
CArtAgO operations and external sessions, causal queued work, NPL transitions and
notifications use one shared admission ledger. READY follows drain/yield of every
admitted step. The shared logical clock then freezes; artifact `await_time`, Jason
`.wait`, and NPL deadlines use that clock. Requested and frozen boundaries differ
when admitted work drains. Publisher, TCP control, disk ingress and inspection
remain available. This is independent of the existing Jason Pause control.

Admitted semantic Task entries acquire intention-identity continuations. A Task
terminal closes admission without waiting and defers that intention's next deed.
Other admitted Tasks on the same agent may finish; future unrelated intentions
cannot start. A multi-act cycle returns after its current act when admission is
closed. Pending actions, waits and nested parents yield with explicit continuation
receipts. Atomic intentions obey the same selected boundary. Runnable Task
continuations and causal queued work must drain before READY; the interpreter's
actual intention objects remain in its queues. Exact release resumes those same
objects. This is a cooperative source boundary, not an atomic stop at the callback
or a rollback of work already admitted elsewhere in the MAS.

Strict preflight rejects unsupported remote/platform schedulers, custom agent
architectures, raw thread/clock/timer and unadmitted producers. This is a bounded
source-admission profile, not a sandbox for arbitrary Java code or a distributed
platform freeze. A non-yielding in-flight operation prevents READY and retains the
hold. Stop cancels the owned process lifecycle; it is not an approval.

Admission profile 1.1 resolves includes using the loader's actual source search
paths (including relative `file:` entries). Source-owned Swing/AWT artifacts
are supported only when callback analysis proves presentation-only callbacks;
custom domain callbacks, timers, executors and raw producers remain rejected.
The pinned GUIArtifact dispatch queue still crosses the common admission barrier.
For admitted Java compilation units, `SourceClockWeaver` checks classpath/source
identity and rewrites only compiled System.currentTimeMillis/nanoTime calls to
the shared frozen logical clock. Derived class bytes join the admitted hashes;
source files are unchanged. Shadowed/unresolved classes fail admission.

`source-provenance.json` identifies upstream source/binaries. `prepare_sources.py`
reconstructs the narrow overlays; `build.py` compiles replacement classes into
distinct Maven versions and writes hashes/profiles. Official Maven bytes remain
unchanged. Rebuild from this workspace (JDK 21+, Python, Maven, upstream source
checkouts under `../../checkpoint-deps`):

```powershell
python checkpoint-dependencies/prepare_sources.py
python checkpoint-dependencies/build.py
.\gradlew.bat test --tests CheckpointFeasibilityTest --tests jacamo.infra.CheckpointSourceAdmissionTest --tests jacamo.infra.GenericSourceAdmissionTest --tests CheckpointCoordinatorTest jar publishToMavenLocal --offline --console=plain
python checkpoint-dependencies/record-jacamo-build.py
python checkpoint-dependencies/run-probes.py
```

Build the USE reactor after publication. `record-jacamo-build.py` binds the actual
JaCaMo JAR into `dependency-lock.json`; the installed launcher verifies these bytes
before starting an owned producer. Evidence and acceptance status are in
`../../use/use-plugin/verification/verified-checkpoints` and `../../use/plan.md`.

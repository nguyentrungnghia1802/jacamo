# JaCaMo source-code model audit

## Audit status and scope

**Audit date:** 2026-09-29  
**Audited checkout:** `D:\_CODE_BANK\Project_\08_Thesis\jacamo`  
**Scope:** source/API audit before any USE transformation.  
**Explicit non-scope:** no USE mapping, Ecore change, plugin implementation, model transformation, or runtime adapter change is made by this document.

This document records what is directly supported by the JaCaMo source tree, the resolved dependency artifacts, exact external source material where available, and bytecode where source was not available. It deliberately separates:

1. source/AST objects;
2. specification/configuration objects; and
3. runtime objects and mutable runtime state.

The labels used below are strict:

- **SOURCE** — directly read from Java/JavaCC/XML/ASL source in the audited checkout or an exact source artifact.
- **BYTECODE** — directly confirmed from the resolved JAR with `javap`; used for CArtAgO because its source artifact was not available.
- **DERIVED** — a small conclusion obtained by following two or more directly evidenced calls. The exact evidence is stated.
- **INFERENCE** — plausible interpretation not established as an API contract.
- **UNKNOWN** — not established by the inspected source/API; it must not be treated as a faithful model fact.

The audit is intentionally conservative. A Java field, a parser action, or a runtime method is reported as an object only when its creation/access path is visible. A string that merely names another object is kept as a reference tuple, not promoted to object identity.

## 1. Version and dependency inventory

### 1.1 JaCaMo source state

| Item | Observed value | Evidence | Audit consequence |
|---|---|---|---|
| JaCaMo project version in build | `1.3.1` | `build.gradle:4-7` | This is the declared build version. |
| JaCaMo checkout commit | `3866858a7ebf6be85d9199c13a09cf4bfb8191be` | `git log -1 --format=fuller` | The checkout is two commits after the `v1.3.1` tag (`git describe`: `v1.3.1-2-g3866858`). |
| JaCaMo branch | `main` | `git status --short --branch` | Source evidence is for this working checkout, not an abstract release. |
| remote | `https://github.com/jacamo-lang/jacamo` | `git remote -v` | Supports source provenance only. |
| Java toolchain | Java 21 | `build.gradle:8-10` | Generated/parser and dependency compatibility is JDK-sensitive. |
| parser source | JavaCC grammar | `src/main/javacc/JaCaMoProjectParser.jj` | Generated parser output is build output; the grammar is the source of truth. |
| local working tree | pre-existing modifications in documentation, readmes, and wrapper files | `git status` before audit | Those changes were preserved; this audit adds one new document only. |

The repository root `D:\_CODE_BANK\Project_\08_Thesis` is not itself the JaCaMo Git repository. The JaCaMo repository is the lower-case `jacamo` child directory. No `artical` repository was present in this checkout, so it is not part of this audit.

### 1.2 Declared direct dependencies

The following coordinates are declared in `jacamo/build.gradle:16-43` and were resolved with:

```text
.\gradlew.bat --no-daemon dependencies --configuration compileClasspath
```

| Coordinate | Role in this audit | Resolution |
|---|---|---|
| `io.github.jason-lang:jason-interpreter:3.3.2` | Jason AgentSpeak syntax, program model, reasoning runtime | resolved |
| `org.jacamo:cartago:3.1` | CArtAgO artifact/workspace API and runtime | resolved |
| `org.jacamo:jaca:3.1` | JaCa/CArtAgO integration used by the JaCaMo platform | resolved |
| `org.jacamo:moise:1.1` | Moise OS specification and OE runtime classes | resolved |
| `org.jacamo:npl:0.6.1` | NPL normative runtime used by organizational boards | resolved |
| `org.jacamo:intmas:1.0.0` | infrastructure/runtime dependency | resolved |
| `org.jacamo:sai:0.5.4` | institutional/SAI bridge dependency | resolved |
| `org.antlr:antlr4:4.12.0` | parser/runtime dependency | resolved |
| `org.jacamo:jacamo-rest:0.9-SNAPSHOT` | REST integration declared with transitive dependency excluded | resolved |
| `org.gradle:gradle-tooling-api:9.7.1` | `RunJaCaMoProject` Gradle launch path | resolved |
| `org.graphstream:gs-core:2.0` and Graphviz Java `0.7.0` | visualization/build support | resolved; not used as source model evidence |
| Vert.x `3.7.0`, Jackson `2.9.8`, Log4j `2.23.1`, JUnit `4.13.1` | support/test/runtime dependencies | resolved; not used as source model evidence |

The Jason interpreter transitively resolves `net.sf.ingenias:jade:4.3` and JSON API/implementation artifacts. Those are not promoted to JaCaMo model concepts in this audit.

### 1.3 Artifact provenance and hashes

The following exact artifacts were inspected from the Gradle cache. SHA-256 is included so later work can detect a silent artifact substitution.

| Artifact | SHA-256 |
|---|---|
| `jason-interpreter-3.3.2.jar` | `CD79268DDCFA45599284BDA64BD6980DBD86EEC6C862028CC276495AAB49154C` |
| `cartago-3.1.jar` | `BF03B37B28E304B2D784C8D77267E56C12B943FF70EED35B533C6323E0736866` |
| `jaca-3.1.jar` | `F53745D4C0202AB2CB0F9B7EEE9F7D21D6EF621706F8BB9B49ED09E63638F72D` |
| `moise-1.1.jar` | `2B7D0415C5EC0BCF060A430C7D12753AA02BDF769172922D10FF2909870161A8` |
| `npl-0.6.1.jar` | `956818EDFE9AEFF7C4FDE35BF1DD276CD71E1DA62DAF77A2A15AB08F87903332` |
| `intmas-1.0.0.jar` | `D99D9DF734222C5AD87654EC5157FF5FE9007E081A91DEE6A6760F43E16B8F7B` |
| `sai-0.5.4.jar` | `22D163D2E694FE64CFC6D3B0FFCA25033805D6629C5721A3ECD107312F0C2C7D` |

Jason’s exact source artifact was available and downloaded from the JaCaMo Maven repository URL declared by the project:

| Source artifact | Provenance |
|---|---|
| `jason-interpreter-3.3.2-sources.jar` | SHA-256 `2F4E450D1FB116A9BF2D4AB7EDDB2265B82FA4A303A97A64570C67710E2D1CE0` |

The Jason Git repository did not expose a `v3.3.2` tag during this audit; therefore the exact `3.3.2` source artifact, not a nearby Git tag, is authoritative for Jason source claims.

Moise source was available by checking out Git tag `v1.1` from `https://github.com/moise-lang/moise.git` at commit `c68d4b7068c56b7a42e657bdc160f55f2d366ea8` (`candidate rel 1.1`).

CArtAgO source/Javadoc artifacts were not present in the configured raw Maven repository, and the attempted public source repositories were unavailable. CArtAgO claims below are therefore marked **BYTECODE**, with the exact JAR hash above. Where bytecode does not establish a semantic detail, the result is **UNKNOWN**.

## 2. JCM Java model: parser and specification objects

### 2.1 Parser pipeline

The JaCaMo project parser is a JavaCC parser whose production actions directly allocate Java model objects:

```text
JCM text
  -> JaCaMoProjectParser.parse(directory)
  -> JaCaMoProject
       -> JaCaMoAgentParameters
       -> JaCaMoWorkspaceParameters / JaCaMoOrgParameters
       -> JaCaMoGroupParameters / JaCaMoSchemeParameters
       -> JaCaMoInstParameters
       -> Jason ClassParameters and string reference tuples
  -> JaCaMoProject.parserFinished()
       -> JaCaMoAgentParameters.fix()
```

**SOURCE evidence:** `JaCaMoProjectParser.parse(String directory)` creates a new `JaCaMoProject`, sets the society name and directory, initializes the agent map, parses the top-level blocks, calls `project.parserFinished()`, and returns the project (`src/main/javacc/JaCaMoProjectParser.jj:170-216`).

The grammar has `STATIC=false` and `UNICODE_INPUT=true` (`JaCaMoProjectParser.jj:1-4`). Its actions retain selected semantic values but do not create a durable token/comment/whitespace AST. Comments and lexical layout are consumed by the parser and are not represented by a public `JaCaMoProject` field. This is **SOURCE**, not an assumption about a future AST.

### 2.2 JCM object inventory

| JCM concept | FQCN | Creation/access evidence | Stored shape | Boundary |
|---|---|---|---|---|
| project | `jacamo.project.JaCaMoProject` | `new JaCaMoProject()` in `JaCaMoProjectParser.parse`, then `setSocName`, `setDirectory`, `initAgMap` (`JaCaMoProjectParser.jj:174-179`) | extends `jason.mas2j.MAS2JProject`; maps/lists for agents, workspaces, organizations, institutions, nodes, platforms, packages | **SOURCE** specification/configuration object |
| agent declaration | `jacamo.project.JaCaMoAgentParameters` | `new JaCaMoAgentParameters(project)` in `agent()` (`JaCaMoProjectParser.jj:221-242`) | Jason agent parameters plus `Set<String> wks`, `List<String[]> roles`, `List<String[]> focus`, and project reference (`JaCaMoAgentParameters.java:22-38`) | not a Jason runtime `Agent` |
| workspace declaration | `jacamo.project.JaCaMoWorkspaceParameters` | `new JaCaMoWorkspaceParameters(project)` in `workspace()` (`JaCaMoProjectParser.jj:290-304`) | name, `Map<String,ClassParameters> artifacts`, debug/host, optional runtime `WorkspaceId` (`JaCaMoWorkspaceParameters.java:12-22`) | `WorkspaceId` is absent until runtime startup |
| artifact declaration | `jason.mas2j.ClassParameters` | `artifact()` parses class definition and calls `wks.addArtifact(t.image, cp)` (`JaCaMoProjectParser.jj:306-321`) | class name plus string parameters, later `getTypedParametersArray()` | declaration only; not a `cartago.Artifact` |
| organization declaration | `jacamo.project.JaCaMoOrgParameters` | `new JaCaMoOrgParameters(project)` in `organisation()` (`JaCaMoProjectParser.jj:324-351`) | organization source/name plus inherited workspace-like artifact map and group/scheme lists | declaration only; not `moise.oe.OE` |
| group declaration | `jacamo.project.JaCaMoGroupParameters` | `new JaCaMoGroupParameters(org)` in `group()` (`JaCaMoProjectParser.jj:353-376`) | id/type, subgroup list, responsible-for list, player assignments | declaration only; role cardinalities are in the referenced Moise OS XML |
| scheme declaration | `jacamo.project.JaCaMoSchemeParameters` | `new JaCaMoSchemeParameters(org)` in `scheme()` (`JaCaMoProjectParser.jj:378-393`) | id/type/params/debug | declaration/instance request; not `moise.os.fs.Scheme` |
| institution declaration | `jacamo.project.JaCaMoInstParameters` | `new JaCaMoInstParameters(project)` in `institution()` (`JaCaMoProjectParser.jj:395-417`) | name/source, workspace names, params, `Object saiRuleEngine` (`JaCaMoInstParameters.java:9-15`) | rule engine type/identity is **UNKNOWN** at this layer |
| agent-role relation | no dedicated relation class | `project.addAgRole(...)` calls `agent.addRole(...)` (`JaCaMoProject.java:270-277`) | raw `String[3]` `[organization, group, role]` (`JaCaMoAgentParameters.java:89-101`) | context-preserving tuple; not an object identity |
| agent-focus relation | no dedicated relation class | `project.addAgFocus(...)` -> `agent.addFocus(...)` (`JaCaMoProject.java:254-268`; `JaCaMoAgentParameters.java:107-124`) | raw `String[3]` `[artifactId, workspaceId, namespace]` | context-preserving tuple; artifact/workspace runtime IDs are not resolved here |

### 2.3 `JaCaMoProject` fields and lifecycle

`jacamo.project.JaCaMoProject` extends `jason.mas2j.MAS2JProject` and implements `Serializable` (`JaCaMoProject.java:15`). Its JaCaMo-specific fields include:

- `Map<String,JaCaMoWorkspaceParameters> workspaces`;
- `List<JaCaMoOrgParameters> orgs`;
- `List<JaCaMoInstParameters> insts`;
- `Map<String,String> nodeHosts`;
- `SourcePath orgPaths` and `SourcePath javaSourcePaths`;
- `Map<String,String[]> platformParameters`;
- deployment properties, run arguments, and package references (`JaCaMoProject.java:21-36`).

The constructor installs default AgentSpeak, organization, and Java source paths (`JaCaMoProject.java:38-52`). `importProject` parses `.jcm` through `new JaCaMoProjectParser(new FileReader(fileName))` and merges the resulting project (`JaCaMoProject.java:75-121`).

`parserFinished()` only calls `fix()` on each agent (`JaCaMoProject.java:191-195`). `fix()` resolves omitted workspace/organization values by looking up the project’s declared artifact workspace/group organization and then adds the resolved workspace (`JaCaMoAgentParameters.java:126-145`). This is a deferred reference normalization pass, not runtime object creation.

The project’s workspace lookup has a compatibility behavior: `getWorkspace(name)` also returns an organization of that name when no workspace is found (`JaCaMoProject.java:208-223`). A consumer must preserve this ambiguity instead of assuming every workspace reference denotes a CArtAgO workspace.

### 2.4 Agent declaration expansion

The parser creates one `JaCaMoAgentParameters` object for an agent declaration. If `instances` is present, the parser calls `ag.copy()`, changes the copied name, and adds each copy to the project (`JaCaMoProjectParser.jj:278-285`). At launch time, `JaCaMoLauncher.createAgs()` filters/expands the agent list, clears the project list, installs the expanded list, fixes agent sources, and delegates to Jason (`JaCaMoLauncher.java:402-442`).

Therefore:

- the JCM source declaration and each launched agent parameter are different model events;
- `instances: N` is represented by repeated `AgentParameters` objects before Jason agent creation;
- `JaCaMoAgentParameters.copy()` copies the role/focus lists but the contained `String[]` values are shallow-copied (`JaCaMoAgentParameters.java:60-72`);
- no CArtAgO or Moise object is created by the parser’s `instances` action.

### 2.5 What the JCM model does not contain

The JCM model does **not** contain:

- Jason `Agent`, `BeliefBase`, `Plan`, `PlanBody`, `Intention`, or `Circumstance` objects;
- a CArtAgO `Artifact`, `ArtifactId`, `ArtifactInfo`, or operation descriptor;
- a Moise `OS`, `SS`, `FS`, `NS`, `OE`, `GroupInstance`, or `SchemeInstance`;
- a durable source/token AST for the `.jcm` file;
- a proven global identity linking a JCM agent name to a CArtAgO `AgentId` or Moise `OEAgent` object.

These absences are directly supported by the declared field types and parser actions. They are not gaps to be silently filled from general JaCaMo terminology.

## 3. Jason source, specification, and runtime object model

The exact Jason source artifact `jason-interpreter-3.3.2-sources.jar` was inspected. Source paths below are paths internal to that artifact.

### 3.1 Agent program/source model

`jason.asSemantics.Agent` has protected fields `BeliefBase bb`, `PlanLibrary pl`, `TransitionSystem ts`, and `String aslSource`, plus private initial-goal and initial-belief lists (`jason/asSemantics/Agent.java:50-65`). `initAg()` lazily creates `DefaultBeliefBase`, `PlanLibrary`, initial lists, internal-action/function maps, and a `TransitionSystem` (`Agent.java:117-136`).

`Agent.parseAS(InputStream,String)` and `parseAS(Reader,String)` create `jason.asSyntax.parser.as2j`, set its source id, and call `parser.agent(this)` (`Agent.java:387-396`). `loadAS` interprets a source string as a classpath resource, URL, or file, then sets the AgentSpeak source reference (`Agent.java:161-182`).

The parser’s exact creation actions are:

| Source construct | Created object/access | Evidence |
|---|---|---|
| initial belief | `Literal b = belief()` then `a.addInitialBel(b)` | `jason/asSyntax/parser/as2j.java:165-167` |
| initial achievement goal | `Literal g = initial_goal()` then `a.addInitialGoal(g)` | `as2j.java:169-172`, `317-325` |
| plan | `Plan p = plan()`, source file assigned, then `a.getPL().add(p)` | `as2j.java:174-189` |
| plan annotation/label | `Pred` created; optional annotations attached; source info attached | `as2j.java:432-490` |
| trigger | `new Trigger(TEOperator, TEType, Literal)` | `as2j.java:494-546` |
| plan body | linked `PlanBody` objects; `setBodyNext` for `;` sequencing | `as2j.java:549-674` |
| include/directive block | a temporary `new Agent()` is initialized for directive processing; parsed components are processed through the directive | `as2j.java:199-224` |

This is the Jason parsed program representation. It is not the original source text as an immutable AST: the parsed plans enter a mutable `PlanLibrary`, beliefs enter mutable storage during initialization, and source positions are retained through `SourceInfo` where present.

### 3.2 Jason specification/program object inventory

| FQCN | Important fields/methods | Creation/access | Classification |
|---|---|---|---|
| `jason.asSemantics.Agent` | `bb`, `pl`, `ts`, `aslSource`, initial beliefs/goals; `parseAS`, `loadAS`, `getBB`, `getPL`, `getTS` | `Agent.create(...)` reflects an agent class, creates `TransitionSystem`, selects/creates a belief base, initializes, and loads source (`Agent.java:88-106`) | program container plus runtime owner |
| `jason.bb.BeliefBase` | add/remove/contains/size/iteration/candidate lookup/percepts | `Agent` installs a `DefaultBeliefBase` unless a custom `ClassParameters` class is supplied (`Agent.java:94-105`) | mutable belief specification/state container |
| `jason.bb.DefaultBeliefBase` | predicate-indexed maps and percept storage in the exact JAR | accessed through `Agent.getBB()` | mutable runtime state; exact indexing is implementation detail |
| `jason.pl.PlanLibrary` | `relPlans`, ordered `plans`, `varPlans`, label map, listeners, mutable lock | parser calls `a.getPL().add(p)`; `add` sets scope, labels, source annotation and relevance indexes (`PlanLibrary.java:29-69`, `113-205`) | mutable parsed program library |
| `jason.asSyntax.Plan` | label, `Trigger tevent`, context, `PlanBody body`, optional goal condition/subplans, source string | parser calls `new Plan(L,T,C,B)` and `setSrcInfo` (`Plan.java:38-74`; `as2j.java:415-424`) | parsed plan object |
| `jason.asSyntax.Trigger` | operator enum `add/del/goalState`, type enum `belief/achieve/test/signal`, `Literal literal` | parser creates it at `as2j.java:495-546`; public getters at `Trigger.java:166-213` | parsed event pattern |
| `jason.asSyntax.PlanBody` / `PlanBodyImpl` | `BodyType`, term, next link, mutable setters/add/remove, iterator | parser builds linked body nodes and calls `setBodyNext` (`as2j.java:549-674`; `PlanBodyImpl.java:27-120`) | parsed/action-sequence structure |
| `jason.asSyntax.Literal` / `LiteralImpl` / `Pred` / `Structure` | functor, namespace, terms, annotations, source info, mutable term/annotation operations | created by Jason parser; accessed by plan, belief and trigger APIs | syntax term graph |
| `jason.asSyntax.SourceInfo` | source id and line/position data | `Plan` receives `new SourceInfo(asSource,start,end)` (`as2j.java:408-417`) | source provenance, not runtime identity |
| `jason.mas2j.AgentParameters` | name, source URI, agent/belief-base classes, instances, options, architecture classes, host | JaCaMo subclass extends it; Jason `Agent.create` consumes class parameters | launch specification |

`Plan.getTerm(int)` exposes the plan as `[label, trigger, context, body]` (`Plan.java:94-110`, `185-194`). The body is a linked mutable structure, not a flat list guaranteed by the API. `PlanBodyImpl` can represent action, internal action, achievement/test, belief update, fork/constraint and other `BodyType` values; a consumer must use `getBodyType`, `getBodyTerm`, and `getBodyNext` rather than infer semantics from string formatting.

### 3.3 Jason runtime object inventory

| FQCN | Runtime state | Creation/access evidence | Boundary |
|---|---|---|---|
| `jason.asSemantics.TransitionSystem` | agent, architecture, `Circumstance`, settings; reasoning-cycle methods | `Agent.initAg()` creates it; access `Agent.getTS()` | runtime controller; not a source AST |
| `jason.asSemantics.Circumstance` | event queue, intentions, pending events/actions, selected event/intention/option, applicable/relevant plans, last deed | exact JAR public accessors `getEvents`, `getRunningIntentions`, `getPendingEvents`, `getAction`, `getApplicablePlans`, `getRelevantPlans`, `getSelectedEvent`, `getSelectedIntention`, `getLastDeed` | highly mutable cycle state |
| `jason.asSemantics.Event` | trigger plus optional intention/option | created by runtime event processing; `getTrigger`, `getIntention`, `getOption` | runtime event identity; not equal to a JCM event |
| `jason.asSemantics.Intention` | id, suspended state/reason, deque of `IntendedMeans`, atomic count | exact JAR constructors/accessors; current means are popped/pushed during reasoning | runtime execution stack |
| `jason.asSemantics.IntendedMeans` | selected `Plan`, current `PlanBody`, trigger and `Unifier` | created from an `Option` and trigger | execution frame |
| `jason.asSemantics.Option` | candidate `Plan`, `Unifier`, originating `Event` | runtime plan selection | transient candidate |
| `jason.asSemantics.ActionExec` | action literal, intention, result/failure reason | runtime action stage; accessed through `Circumstance` | transient execution result |
| `jason.architecture.AgArch` | agent architecture and external integration callbacks | JaCaMo supplies `JaCaMoAgArch` | integration boundary |
| `jason.asSemantics.InternalAction` | executable Java internal action interface | `Agent.getIA` reflectively loads a class or static factory, then caches it (`Agent.java:398-415`) | dynamic code/plugin boundary |

The exact Jason runtime classes have mutable collections and mutable execution stacks. A snapshot consumer must distinguish a stable source/program view from a cycle-time observation.

### 3.4 JaCaMo-to-Jason runtime handoff

`jacamo.infra.JaCaMoAgArch.init()` obtains `JaCaMoAgentParameters` from the Jason settings user-parameter map under `Settings.PROJECT_PARAMETER` (`JaCaMoAgArch.java:30-43`). It turns workspace/focus/role parameters into Jason literals such as `art_env`, `focused`, and `role`, then adds initial achievement goals to the transition system (`JaCaMoAgArch.java:45-122`).

This proves a one-way integration path:

```text
JaCaMoAgentParameters (strings/tuples)
  -> JaCaMoAgArch.init()
  -> Jason beliefs/events/goals
  -> Jason Agent/TransitionSystem runtime
```

It does not prove that the original parameter object remains the owner of those runtime literals, or that a runtime literal is a globally unique reference to a CArtAgO/Moise object. Those are separate object domains.

## 4. CArtAgO type and runtime object model

The exact resolved artifact is `org.jacamo:cartago:3.1`. No matching source artifact was available, so the following section is **BYTECODE** unless explicitly marked as source from the JaCaMo checkout.

### 4.1 Artifact type/specification level

The user-defined type in the repository is a Java class extending `cartago.Artifact`, for example `auction_env.AuctionArtifact` (`examples/auction/src/env/auction_env/AuctionArtifact.java:6`) and `tools.AuctionArt` (`examples/house-building/src/env/tools/AuctionArt.java:10`). The type is Java bytecode, not a runtime artifact instance.

The JAR confirms:

- `cartago.Artifact` is an abstract class;
- `cartago.OPERATION` is a runtime-retained annotation with `String guard()`;
- `cartago.GUARD` is a runtime-retained annotation with `String[] value()`;
- `Artifact.setupOperations()` walks the concrete class and superclasses using `Class.getDeclaredMethods()`;
- it checks `@OPERATION`, `@LINK`, `@INTERNAL_OPERATION`, and `@GUARD`, builds `ArtifactOpMethod`, `ArtifactGuardMethod`, and `OpDescriptor` objects, and stores them in operation/guard maps;
- operation keys are derived from the method name and parameter count, with varargs using a special key;
- operation invocation ultimately uses reflective `Method.invoke`.

The relevant bytecode is in `cartago/Artifact.class`, method `setupOperations()`, and is independently visible through `javap -c -p`. Therefore the type-level operation model is:

```text
Artifact subclass bytecode
  -> runtime annotation scan
  -> ArtifactOpMethod / ArtifactGuardMethod
  -> OpDescriptor operation map
  -> Workspace operation map
```

`@OPERATION` is not a JCM artifact declaration and does not create an instance by itself. The JCM declaration supplies a class name and constructor/configuration strings through `jason.mas2j.ClassParameters`.

### 4.2 CArtAgO runtime object inventory

| FQCN | Important members/API | Creation/access | Classification |
|---|---|---|---|
| `cartago.Workspace` | workspace id, artifact descriptors/maps, joined agents, operation maps; `joinWorkspace`, `makeArtifact`, `getArtifactInfo`, `focus`, `stopFocus` | `Workspace.makeArtifact(AgentId,String,String,ArtifactConfig)` returns `ArtifactId`; `Cartago.start` calls this path | runtime container |
| `cartago.WorkspaceId` | name/full name/UUID | `Workspace.getId`; JaCaMo stores it in `JaCaMoWorkspaceParameters.wId` | runtime identity |
| `cartago.Artifact` | protected `ArtifactId`, workspace, operation/guard maps, observable-property map, current-operation context, creator id | constructed by workspace factory; `getId()` is protected; subclass code can access protected hooks | live mutable artifact instance |
| `cartago.ArtifactId` | name, artifact type, workspace id, creator id, UUID; public getters | returned by `Workspace.makeArtifact`; passed to `Op` calls/focus | runtime reference/identity token |
| `cartago.ArtifactDescriptor` | actual `Artifact`, creator `AgentId`, adapter, artifact type/observer state | workspace’s internal artifact registry; `getArtifactDescriptor` | registry wrapper |
| `cartago.OpDescriptor` | operation key, `IArtifactOp`, `IArtifactGuard`, operation type (`LINK`, `UI`, `INTERNAL`), dynamic flag | built by `Artifact.setupOperations`, registered in workspace | operation metadata |
| `cartago.ArtifactOpMethod` | artifact plus reflective `Method`; operation name/arity/varargs | built while scanning annotations | executable operation wrapper |
| `cartago.ArtifactGuardMethod` | artifact plus reflective guard `Method` | built while scanning guard annotations | executable guard wrapper |
| `cartago.Op` | name plus `Object[]` parameter values | `new Op(name, params)` in a Jason/CArtAgO call or JaCaMo platform call | operation request value |
| `cartago.AgentId` | local id, agent name/role, workspace id, global id | supplied by joined workspace/context; accessible through context and artifact current-op methods | CArtAgO caller identity |
| `cartago.ObsProperty` | live name/values/id/annotations and update/commit/rollback methods | created by `Artifact.defineObsProperty`; accessed by `Artifact.getObsProperty` | live mutable property |
| `cartago.ArtifactObsProperty` | serializable name/id/values/annotations snapshot | returned in artifact info/focus/event paths | observation snapshot, not live property object |
| `cartago.ArtifactInfo` | `AgentId`, `ArtifactId`, operation descriptors, observed-property snapshots, operation info, linked artifacts, manual | public workspace/controller inspection API | externally consumable runtime snapshot |
| `cartago.ICartagoContext` | `doAction`, workspace/agent access, quit | returned by `Workspace.joinWorkspace` | caller context/transport boundary |

### 4.3 JaCaMo CArtAgO creation path

`jacamo.platform.Cartago` is a `DefaultPlatformImpl` with a `CartagoEnvironment` (`Cartago.java:21-31`). On startup it obtains the CArtAgO root workspace, iterates JCM workspace declarations, creates each workspace, and writes the returned `WorkspaceId` into the corresponding `JaCaMoWorkspaceParameters` (`Cartago.java:34-65`).

For each JCM artifact declaration, it calls:

```text
currentWks.makeArtifact(
    context.getAgentId(),
    artifactName,
    classParameters.getClassName(),
    new ArtifactConfig(classParameters.getTypedParametersArray())
)
```

(`Cartago.java:74-89`). The result is a CArtAgO `ArtifactId`; the Java artifact subclass instance is created inside CArtAgO’s workspace factory. JaCaMo’s JCM object holds the declaration and, at the workspace level, the `WorkspaceId`; it does not expose a field for every resulting `ArtifactId`.

### 4.4 Operation, observation, focus, and identity boundaries

The auction source gives concrete evidence of the boundary:

- `AuctionArtifact.init` defines four observable properties (`AuctionArtifact.java:10-16`);
- `start`, `stop`, and `bid` mutate live properties (`AuctionArtifact.java:18-43`);
- `bid` obtains the caller from `getCurrentOpAgentId().getAgentName()` (`AuctionArtifact.java:38-43`);
- AgentSpeak calls operations with an `[artifact_id(AId)]` annotation (`auction_capabilities.asl:23-25`, `32-42`);
- `focus(ArtifactId)` creates an observation relationship; the exact callback/event lifecycle is CArtAgO runtime behavior, not a JCM specification field.

The following identities must remain distinct:

| Identity | Evidence | Do not collapse into |
|---|---|---|
| Java artifact class name | `ClassParameters.getClassName()` and `makeArtifact(..., className, ...)` | artifact instance id |
| artifact declaration name | JCM `artifact gui: ...` key | guaranteed global UUID |
| `ArtifactId` | CArtAgO object with workspace and UUID | Java class or live `Artifact` reference |
| `AgentId` | CArtAgO caller identity | Jason agent name string without an explicit adapter path |
| `WorkspaceId` | CArtAgO workspace identity | JCM workspace name before startup |
| `ObsProperty` | live mutable property in artifact | `ArtifactObsProperty` snapshot |

CArtAgO signals are methods on `Artifact` (`signal(...)`) and event/filter classes; there is no `cartago.SIGNAL` annotation class in the audited JAR. A source consumer must not invent one.

## 5. Moise specification and runtime object model

Moise source was inspected at Git tag `v1.1`, commit `c68d4b7068c56b7a42e657bdc160f55f2d366ea8`, and cross-checked against the resolved `moise-1.1.jar`.

### 5.1 Specification object graph

`moise.os.OS` owns an `SS`, `FS`, and `NS` (`moise/os/OS.java:37-51`, `87-89`). `OS.loadOSFromURI` parses and schema-validates XML, allocates an `OS`, and calls `setFromDOM` (`OS.java:143-174`). `setFromDOM` creates fresh `SS`, `FS`, and `NS` objects and delegates to their DOM parsers (`OS.java:176-214`).

| FQCN | Specification fields/API | Evidence | Classification |
|---|---|---|---|
| `moise.os.OS` | `SS ss`, `FS fs`, `NS ns`, URI; getters | constructor and XML loader (`OS.java:37-59`, `143-214`) | root organizational specification |
| `moise.os.ss.SS` | root group spec, role map, link types, OS reference | `SS.java:29-52`, role/group DOM parsing `188-220` | structural specification |
| `moise.os.ss.Role` | id, super-role set, abstract flag, SS reference; links, compatibilities, norms | `Role.java:27-49`, `232-236` | role definition |
| `moise.os.ss.Group` | `CardinalitySet<Role> roles`, `CardinalitySet<Group> subgroups`, parent, links, compatibilities | `Group.java:33-44`; `set/getRoleCardinality` `122-143`; subgroup cardinality `296-306` | group specification |
| `moise.os.ss.RoleRel`, `Link`, `Compatibility` | source/target roles, scope, relation type, direction/extension flags | exact source classes and group relation collections | structural relation objects |
| `moise.os.fs.FS` | scheme collection and scheme/goal lookup | `FS.java:25-52`, `findScheme` | functional specification container |
| `moise.os.fs.Scheme` | mission cardinalities, plan set, goal map, root goal, FS reference | `Scheme.java:32-46`, `86-139` | scheme specification |
| `moise.os.fs.Mission` | goal set, preference set, scheme reference | `Mission.java:26-85` | mission specification |
| `moise.os.fs.Goal` | goal type, plan/in-plan refs, args, dependencies, description, location, `ttf` string, minimum agents | `Goal.java:26-40`, `160-184` | goal specification |
| `moise.os.fs.Plan` | target goal, sub-goals, operator (`sequence`, `choice`, `parallel`), success rate | exact `Plan.java` source | functional plan specification |
| `moise.os.ns.NS` | `OpTypes {obligation, permission}`, norm set, NPL program string | `NS.java:24-66` | normative specification container |
| `moise.os.ns.Norm` | condition, role, mission, `TimeConstraint`, namespace, operation type | `Norm.java:22-102`, XML time constraint `144-151` | norm specification |
| `moise.common.CardinalitySet<T>` | object-to-cardinality associations plus contents map | exact source/JAR; used by `Group` and `Scheme` | relation-scoped cardinality store |

A key source fact is that cardinalities are not universal attributes of a role or group. `Group` stores role cardinalities in `CardinalitySet<Role>` and subgroup cardinalities in `CardinalitySet<Group>`, accessed with the containing group context (`Group.java:36-38`, `122-143`, `296-306`). `Scheme` separately stores mission cardinalities in `CardinalitySet<Mission>` (`Scheme.java:36-38`, `106-124`). Any later structural representation must preserve the owner relation and the tuple `(container, member, min, max)`.

Moise keeps `Goal.ttf` as a `String` (`Goal.java:37-40`, `180-184`) and `Norm` stores a `TimeConstraint` object whose source is also a textual constraint (`Norm.java:26-30`, `89-94`, `144-151`). The source does not establish a canonical numeric duration or a universal scheduler at this layer. Numeric/time semantics are **UNKNOWN** unless separately proven in the runtime implementation.

### 5.2 Moise runtime object graph

| Specification object | Runtime object | Creation/access evidence | State |
|---|---|---|---|
| `moise.os.OS` | `moise.oe.OE` | `OE` stores an `OS`; `OE.createOE` loads the OS and constructs an OE with a purpose goal (`OE.java:40-75`) | live organizational entity |
| `moise.os.ss.Role` | `moise.oe.RolePlayer` | `OEAgent.adoptRole` validates the role/group and creates `new RolePlayer(role,this,gr)` (`OEAgent.java:76-124`) | agent-role membership |
| `moise.os.ss.Group` | `moise.oe.GroupInstance` | `OE.addGroup` checks root/cardinality, creates `new GroupInstance(grId,rootSpec)`, stores it in `groups`, and attaches OE (`OE.java:244-290`) | live group instance |
| `moise.os.fs.Scheme` | `moise.oe.SchemeInstance` | `OE.startScheme` resolves the scheme spec, creates/stores `SchemeInstance`, and attaches OE (`OE.java:378-414`) | live scheme instance |
| `moise.os.fs.Goal` | `moise.oe.GoalInstance` | `SchemeInstance` constructs a goal instance for every scheme goal and stores them by id (`SchemeInstance.java:62-89`) | live goal state |
| `moise.os.fs.Plan` | `moise.oe.PlanInstance` | `SchemeInstance` constructs `PlanInstance` objects and links their goal instances (`SchemeInstance.java:81-89`; `PlanInstance.java:28-67`) | live plan execution/formation structure |
| `moise.os.fs.Mission` | `moise.oe.MissionPlayer` | `OEAgent.commitToMission` validates and creates a `MissionPlayer`, then adds it to the scheme (`OEAgent.java:329-374`) | live agent commitment |
| `moise.oe.GoalInstance` | same object with state | `setAchieved`, `setImpossible`, `committed`, `uncommitted`, `getState`, committed/achieved agent lists (`GoalInstance.java:33-52`, `138-244`, `329-344`) | mutable goal state |

`moise.oe.OE` holds maps of `OEAgent`, `GroupInstance`, and `SchemeInstance` keyed by runtime ids (`OE.java:40-49`). `addAgent` creates and stores a fresh `OEAgent` (`OE.java:158-174`). The runtime agent itself keeps role memberships keyed by `groupId.roleId` and mission commitments keyed by `schemeId.missionId` (`OEAgent.java:39-43`).

`SchemeInstance` keeps its specification reference, root goal, OE reference, mission players, responsible groups, goal instances, and plan instances (`SchemeInstance.java:36-49`). Its `isWellFormed` checks mission-player counts against the scheme’s mission cardinalities (`SchemeInstance.java:213-260`); `isCommitable` checks root goal state (`SchemeInstance.java:262-265`). This is runtime state, not merely the XML plan.

`GoalInstance` is not the same type as `moise.os.fs.Goal`. It contains a specification reference, plan links, state enum (`waiting`, `enabled`, `satisfied`, `impossible`), argument values, committed agents, and achieved agents (`GoalInstance.java:33-52`). The implementation rejects achievement by an agent not committed to the goal and updates state based on committed/achieved cardinality (`GoalInstance.java:138-207`).

### 5.3 JaCaMo organizational startup path

`jacamo.platform.Moise` does not directly expose a `moise.oe.OE` object in its JaCaMo startup code. It creates or joins a CArtAgO workspace, creates an organizational board artifact (`sai.bridges.jacamo.OrgBoardSai` when an institution is present, otherwise `ora4mas.nopl.OrgBoard`), stores the workspace id, and sends board operations to create schemes/groups and relations (`jacamo/platform/Moise.java:22-93`, `104-179`).

This produces two distinct, source-supported possibilities:

1. direct Moise-library runtime objects (`OS`/`OE`/`GroupInstance`/`SchemeInstance`) when code uses the Moise API; or
2. the JaCaMo launcher’s CArtAgO organization-board path, where JaCaMo sends `Op("createGroup",...)`, `Op("createScheme",...)`, and related operations to a board artifact.

The JaCaMo source inspected here proves the second path. It does **not** prove that an externally accessible direct `moise.oe.OE` is the authoritative state for every running JaCaMo organization. A runtime adapter must represent the board path explicitly or mark direct OE state as **UNKNOWN**.

## 6. Cross-component identity and reference audit

### 6.1 Agent identity

There are at least four agent identity layers:

| Layer | Representation | Creation/access |
|---|---|---|
| JCM declaration | `String name` in `jason.mas2j.AgentParameters`/`JaCaMoAgentParameters` | parser action and `AgentParameters.name` |
| Jason runtime | Jason architecture/agent name, `jason.asSemantics.Agent` and `AgArch` | `Agent.create`, launcher, `JaCaMoAgArch` |
| CArtAgO runtime | `cartago.AgentId` containing agent/workspace/global identity | workspace join/context and `getCurrentOpAgentId` |
| Moise runtime | `moise.oe.OEAgent` keyed by OE string id | `OE.addAgent(String)` |

The audited source provides string-based bridging and injected Jason literals, but no universal object identity function from one layer to another. A safe cross-component record is therefore:

```text
AgentReference {
    declaredName: String
    jasonName: String or UNKNOWN
    cartagoAgentId: opaque AgentId or UNKNOWN
    moiseAgentId: String/OEAgent reference or UNKNOWN
    evidence: source/API observation
}
```

Equality of the strings may be a useful **DERIVED** correlation in a particular launch, but it is not a source-proven universal identity invariant.

### 6.2 Workspace and artifact identity

JCM stores workspace names and artifact declaration keys. At startup, CArtAgO assigns `WorkspaceId` and `ArtifactId`. Agent focus entries remain raw `[artifactId, workspaceId, namespace]` tuples until `JaCaMoAgArch` uses them to create observations/beliefs. `cartago.ArtifactId` itself contains UUID/workspace/creator/type fields, so a declaration name is not sufficient to identify the live instance.

The correct relation chain is:

```text
JCM workspace name
  -> JaCaMoWorkspaceParameters
  -> Cartago.start creates Workspace
  -> WorkspaceId

JCM artifact declaration name + ClassParameters
  -> Workspace.makeArtifact(...)
  -> ArtifactId + live Artifact + ArtifactDescriptor
```

No audited field stores a permanent one-to-one mapping from every declaration key to every later `ArtifactId`. If such a mapping is needed, it must be captured at runtime and labeled as an observation.

### 6.3 Organization identity

JCM organization/group/scheme names are stored in parameter objects. The startup adapter creates a board artifact and sends names/type/source strings through CArtAgO operations. In direct Moise, `OE` maps strings to `OEAgent`, `GroupInstance`, and `SchemeInstance` objects. The same textual id can therefore occur in distinct organizations/OEs and distinct runtime instances. A relation must retain its organization/OE/container context.

### 6.4 Cardinality identity

The exact Moise source proves three distinct owner contexts:

```text
GroupSpec -- role --> Cardinality
GroupSpec -- subgroup --> Cardinality
SchemeSpec -- mission --> Cardinality
```

A standalone `RoleCardinality(role)` or `MissionCardinality(mission)` without its group/scheme owner is not a faithful representation of the source API.

## 7. Case-study audit

This section is static/source evidence. It does not claim a successful live end-to-end run.

### 7.1 Hello World tutorial

The primary project is `doc/tutorials/hello-world/code/helloworld/helloworld.jcm`.

| Source construct | Evidence | Object interpretation |
|---|---|---|
| five named agent declarations | `helloworld.jcm:13-40` | JCM `JaCaMoAgentParameters`, later Jason agents; `bob` has initial belief `country(it)` and focuses two workspaces |
| focus declarations | `helloworld.jcm:15-17`, `21-22`, `26-27`, `31-32`, `39` | raw focus tuples, later CArtAgO observations/Jason literals; not live artifact ids at parse time |
| workspaces and `display.GUIConsole` artifacts | `helloworld.jcm:42-60` | `JaCaMoWorkspaceParameters` with `ClassParameters`; actual `GUIConsole` artifact is runtime-created by CArtAgO |
| organization/group/scheme instance | `helloworld.jcm:62-71` | JaCaMo org/group/scheme parameter objects; referenced OS XML supplies role/group/mission/goal specifications |
| group players | `helloworld.jcm:63-68` | raw `[agent,role]` assignments qualified by organization/group; parser calls `addAgRole` |
| source focus predicates | `hello.asl:1-16`, `hf.asl:1-12` | Jason plans query `focused`, `artifact_id`, `artifact_name`, `artifact_type`; these are runtime beliefs/annotations supplied by the CArtAgO/Jason integration |
| Moise XML role/group/plan/norm model | `src/org/o1.xml:14-104` | exact Moise specification objects when the XML is loaded; `ttf` remains textual and mission cardinalities belong to the scheme |

The Hello World case demonstrates all three layers: JCM configuration, Moise XML specification, and AgentSpeak runtime plans. It does not demonstrate that a JCM artifact declaration object and a runtime `cartago.Artifact` are the same object.

### 7.2 Auction example

The project is `examples/auction/auction.jcm`.

Static evidence:

- five agents are declared; `bob` receives two initial `do_auction` goals (`auction.jcm:8-19`);
- organization `aorg`, group `agrp`, and player-role assignments are declared (`auction.jcm:21-31`);
- `auction-os.xml` defines `auctioneer` and `participant` roles, group cardinalities, `doAuction` scheme, goal arguments, missions, and norms (`auction-os.xml:12-51`);
- `auction_capabilities.asl` dynamically creates a scheme through `createScheme`, sets goal arguments, adds a responsible group, and commits a mission (`auction_capabilities.asl:2-13`);
- the same ASL dynamically creates `auction_env.AuctionArtifact`, focuses it, calls `start`/`bid`/`stop`, and reads observable properties (`auction_capabilities.asl:19-57`);
- `AuctionArtifact` is a live CArtAgO type with `@OPERATION` methods and mutable observable properties (`AuctionArtifact.java:6-43`).

The source proves representation of scheme goals, mission roles, operation calls, and artifact observations. It does not, by itself, prove autonomous enforcement of every `ttf`/deadline or a complete self-referencing live auction plan. Such claims require a separate runtime trace and must remain **UNKNOWN** here.

### 7.3 House-Building example

The project is `examples/house-building/house-building.jcm`.

The JCM file declares agents and expands `companyC` to five and `companyD` to thirteen instances (`house-building.jcm:7-19`). It does not declare a workspace or organization block. The organization is created from AgentSpeak/runtime code:

- `giacomo.asl` creates eight auction artifacts, waits five seconds in an explicit AgentSpeak `.wait(5000)`, creates an `ora4mas` workspace and `OrgBoard`, creates a group, adopts a role, creates a scheme, and commits to a mission (`giacomo.asl:28-100`);
- `house-os.xml` supplies role inheritance, role cardinalities, links, compatibility, a sequence/parallel functional plan, mission cardinalities, and norms (`house-os.xml:14-124`);
- `AuctionArt` is a CArtAgO artifact type with `task`, `maxValue`, `currentBid`, and `currentWinner` properties and an annotated `bid` operation (`AuctionArt.java:10-28`);
- `simulator.House` extends `GUIArtifact` and exposes building operations such as `prepareSite`, `layFloors`, `buildWalls`, and painting/install operations (`House.java:12-74`);
- company agents use raw artifact observations and organizational operations through included AgentSpeak code (`inc/org_code.asl:12-35`).

This case is a direct warning against treating `.jcm` as the complete system specification. A model based only on `house-building.jcm` would miss the ASL-created organization, dynamic artifacts, the referenced Moise OS, and the GUI artifact type.

## 8. Candidate `JacamoSpecificationModel` (audit proposal only)

This is a candidate vocabulary derived from the inspected source. It is **not** a USE mapping or implementation design. Its purpose is to preserve source distinctions before any transformation decision.

### 8.1 Project/configuration layer

```text
JacamoProjectSpec
  societyName: String
  projectDirectory: String
  agentSpecs: [JacamoAgentSpec]
  workspaceSpecs: [JacamoWorkspaceSpec]
  organizationSpecs: [JacamoOrganizationSpec]
  institutionSpecs: [JacamoInstitutionSpec]
  nodeHosts: [(nodeName, host)]
  platformParameters: [(platformName, String[])]
  sourcePaths: {asl, org, java}
  packages: [String]
  inheritedJasonProjectData: opaque/explicitly enumerated
```

```text
JacamoAgentSpec
  declaredName: String
  source: URI/String
  instances: Int
  agentClass: JasonClassParameters?
  beliefBaseClass: JasonClassParameters?
  architectureClasses: [String]
  host: String?
  options: [String]
  initialBeliefs: [JasonLiteralText or parsed Literal]
  initialGoals: [JasonLiteralText or parsed Literal]
  workspaceRefs: [String]
  roleRefs: [(organizationName, groupName, roleName)]
  focusRefs: [(artifactName, workspaceName?, namespace?)]
```

The raw role/focus tuples are intentional. They reflect the actual Java fields and retain the relation context needed for later resolution.

```text
JacamoWorkspaceSpec
  name: String
  host: String?
  debug: String?
  artifactDecls: [JacamoArtifactDecl]

JacamoArtifactDecl
  declarationName: String
  classParameters: JasonClassParameters

JacamoOrganizationSpec extends JacamoWorkspaceSpec
  source: String
  groups: [JacamoGroupSpec]
  schemes: [JacamoSchemeSpec]
  organizationParameters: [(name, String)]

JacamoGroupSpec
  name: String
  type: String
  parentGroupName: String?
  subgroups: [JacamoGroupSpec]
  responsibleSchemeNames: [String]
  players: [(agentName, roleName)]

JacamoSchemeSpec
  name: String
  type: String
  parameters: [(name, String)]

JacamoInstitutionSpec
  name: String
  source: String
  workspaceNames: [String]
  parameters: [(name, String)]
  ruleEngine: opaque Object?
```

### 8.2 Jason source/program layer

The JaCaMo project model only stores the AgentSpeak source reference and initial literal parameters. If a faithful AgentSpeak program model is required, it must be a separate layer backed by Jason’s actual classes:

```text
JasonProgramSpec
  sourceId: String
  initialBeliefs: [Literal]
  initialGoals: [Literal]
  planLibrary: [Plan]

JasonPlanSpec
  label: Pred?
  trigger: Trigger
  context: LogicalFormula?
  body: PlanBody
  goalCondition: LogicalFormula?
  subplans: [Plan]
  sourceInfo: SourceInfo?
```

`PlanBody` must remain a chain/tree-capable API object, and `Literal`/`Term` identity and source information must not be reduced to an untyped display string if semantic preservation is required.

### 8.3 Moise specification layer

```text
MoiseOSSpec
  id: String
  uri: String?
  structural: MoiseSSSpec
  functional: MoiseFSSpec
  normative: MoiseNSSpec

MoiseSSSpec
  roles: [RoleSpec]
  rootGroup: GroupSpec
  linkTypes: [String]

GroupSpec
  id: String
  roleCardinalities: [(groupId, roleId, min, max)]
  subgroupCardinalities: [(groupId, subgroupId, min, max)]
  links: [RoleRelationSpec]
  compatibilities: [RoleRelationSpec]

MoiseFSSpec
  schemes: [SchemeSpec]

SchemeSpec
  id: String
  goals: [GoalSpec]
  plans: [PlanSpec]
  missions: [(schemeId, missionId, min, max, goalIds)]

GoalSpec
  id: String
  type: GoalType
  arguments: [(id, default)]
  dependencies: [GoalId]
  ttf: String

MoiseNSSpec
  norms: [NormSpec]
  nplProgram: String?
```

The relation-scoped cardinality tuples are required by the source API. This proposal does not flatten them into universal attributes.

## 9. Candidate `JacamoRuntimeModel` (audit proposal only)

The JaCaMo launcher has more than one runtime boundary. A candidate runtime vocabulary should therefore expose backend-specific handles and evidence quality rather than pretend that one Java object graph is always available.

```text
JacamoRuntime
  projectSpec: JacamoProjectSpec
  jasonAgents: [JasonAgentRuntime]
  cartagoWorkspaces: [CartagoWorkspaceRuntime]
  artifacts: [CartagoArtifactRuntime]
  organizationBoards: [OrganizationBoardRuntime]
  directMoiseEntities: [MoiseOERuntime]       // optional; UNKNOWN unless observed
  observations: [RuntimeObservation]
```

```text
JasonAgentRuntime
  agentName: String
  source: String?
  agentObject: jason.asSemantics.Agent?
  transitionSystem: jason.asSemantics.TransitionSystem?
  architecture: jason.architecture.AgArch?
  beliefBase: jason.bb.BeliefBase?
  planLibrary: jason.pl.PlanLibrary?
  circumstance: jason.asSemantics.Circumstance?
```

```text
CartagoWorkspaceRuntime
  declaredName: String?
  workspaceId: cartago.WorkspaceId
  workspaceObject: cartago.Workspace?

CartagoArtifactRuntime
  declarationName: String?
  artifactId: cartago.ArtifactId
  artifactObject: cartago.Artifact?             // protected/internal access may be unavailable
  descriptor: cartago.ArtifactDescriptor?
  infoSnapshot: cartago.ArtifactInfo?
  observableProperties: [cartago.ArtifactObsProperty]
  operationDescriptors: [cartago.OpDescriptor]
```

```text
MoiseOERuntime
  osSpec: moise.os.OS
  oe: moise.oe.OE
  agents: [moise.oe.OEAgent]
  groups: [moise.oe.GroupInstance]
  schemes: [moise.oe.SchemeInstance]

OrganizationBoardRuntime
  boardArtifactId: cartago.ArtifactId
  workspaceId: cartago.WorkspaceId
  backendType: String
  lastObservedOperations: [CartagoOperationObservation]
```

Every runtime reference above must carry evidence metadata:

```text
RuntimeObservation
  source: {STATIC, BYTECODE, LIVE_TRACE, REPLAY}
  timestamp: Instant?
  objectType: String
  objectId: opaque/String?
  operation: String?
  payload: opaque/serialized snapshot?
  confidence: {PROVEN, DERIVED, UNKNOWN}
```

The current audit supplies STATIC and BYTECODE evidence. It does not supply a new LIVE_TRACE, so all claims about live object population, board state, deadline enforcement, or autonomous cross-process synchronization remain unverified here.

## 10. Unsupported and unknown items

The following items are explicitly not established by this audit:

1. **Complete JCM source AST.** The JavaCC grammar constructs parameter objects and discards lexical details. A source-preserving AST would require a separate parser design and is not present in the audited JaCaMo Java model.
2. **CArtAgO source-level contract.** Exact JAR bytecode establishes annotation retention, reflection scanning, operation descriptors, ids, and public accessors. It does not establish undocumented behavior that is not visible in the bytecode or a source artifact.
3. **Universal JCM-agent/Jason-agent/CArtAgO-AgentId/Moise-OEAgent identity.** The source provides names and adapters, not a universal identity invariant.
4. **Universal live Moise `OE` ownership for JaCaMo organizations.** JaCaMo startup uses CArtAgO organization-board operations; direct `moise.oe.OE` state is not exposed by the inspected launcher path.
5. **Semantic enforcement of `Goal.ttf` and norm time constraints.** Both are represented textually in the Moise source model. Their precise scheduler/deadline semantics are not inferred from XML strings.
6. **Full autonomous auction/house-building semantics.** Static plans and source snippets demonstrate representation only. No complete live trace was produced in this audit.
7. **Stable artifact declaration to `ArtifactId` mapping.** `ArtifactId` is assigned by CArtAgO runtime creation; the JCM declaration object does not contain the later id.
8. **Public access to the live `cartago.Artifact` object.** `Artifact.getId()` is protected and several artifact internals are private; generic external inspection should use `ArtifactInfo`/`ArtifactDescriptor` where available.
9. **Immutable source/program objects.** Jason `BeliefBase`, `PlanLibrary`, plans, plan bodies, Moise OE collections, and CArtAgO observable properties are mutable.
10. **Complete semantics of custom packages, internal actions, platform parameters, deployment nodes, SAI rule engines, and external board transports.** The fields and calls are recorded; unsupported behavior remains `UNKNOWN` until source/API/trace evidence is added.

## 11. API stability and coupling risks

### 11.1 High-risk coupling points

| Risk | Evidence | Consequence |
|---|---|---|
| Jason version/API drift | JaCaMo pins `jason-interpreter:3.3.2`; `Agent`, `Plan`, `PlanBodyImpl`, and `PlanLibrary` are external APIs | AST/runtime consumers must pin and hash the Jason artifact |
| Generated JavaCC parser coupling | parser class generated under build output from `src/main/javacc` | do not treat generated line numbers/classes as independent source contracts |
| Mutable Jason libraries | `PlanLibrary` keeps mutable ordered/indexed collections and `add` changes labels/scope/source annotations | snapshots need a defined time and defensive copy |
| Reflection-based CArtAgO operations | `Artifact.setupOperations` scans declared methods, annotations, inherited classes, arity, guards and varargs | Java visibility/annotation/signature changes can silently alter operation availability |
| CArtAgO source unavailable | exact `cartago-3.1.jar` only | bytecode inspection must be repeated when source semantics are needed |
| Runtime id assignment | `Workspace.makeArtifact` and `WorkspaceId`/`ArtifactId` creation occur after JCM parse | parse-time model cannot contain authoritative live ids |
| CArtAgO live vs snapshot property types | `ObsProperty` vs `ArtifactObsProperty` | do not treat observation snapshots as mutable artifact state |
| Moise owner-scoped cardinality | `Group` and `Scheme` use `CardinalitySet` keyed by containing relation | flattening loses semantics |
| Moise specification/runtime type pairs | `Goal`/`GoalInstance`, `Plan`/`PlanInstance`, `Group`/`GroupInstance`, `Scheme`/`SchemeInstance` | class-name-only identity is unsafe |
| Moise textual time values | `Goal.ttf` and `TimeConstraint` are textual | no numeric/deadline claim without a separate parser/engine audit |
| JaCaMo parameter inheritance | `JaCaMoGroupParameters extends JaCaMoOrgParameters extends JaCaMoWorkspaceParameters`; institutions also extend workspace parameters | Java inheritance is not a clean domain taxonomy; use declared fields and parser context |
| Shallow copy of agent relations | `JaCaMoAgentParameters.copy` copies lists but not contained arrays | mutation/aliasing behavior matters for a faithful snapshot |
| Board-based organization startup | `jacamo.platform.Moise` sends CArtAgO `Op`s to board artifacts | direct Moise Java runtime inspection can be disconnected from authoritative board state |
| External class names in project files | `ClassParameters` resolves Java class names reflectively | source/classpath/package changes affect runtime object creation |

### 11.2 Recommended evidence discipline for later work

1. Pin the JaCaMo commit, dependency coordinates, artifact hashes, and JDK.
2. Keep source/specification objects separate from runtime snapshots.
3. Preserve relation owner context for all cardinalities and role/mission assignments.
4. Capture CArtAgO `ArtifactId`, `WorkspaceId`, and `AgentId` at the creation/join boundary if a live identity graph is required.
5. Inspect board operations or direct Moise APIs explicitly; do not substitute one for the other.
6. Treat reflection, generated parser output, protected fields, and mutable collection access as compatibility risks.
7. Mark unobserved runtime fields `UNKNOWN` instead of deriving them from the JCM text.

## 12. Evidence table

The table below is the compact audit index. The FQCN and method/field are included so each claim can be rechecked without relying on this document’s prose.

| ID | Claim | FQCN / method or field | Evidence | Status |
|---|---|---|---|---|
| E01 | JCM parser creates project object | `jacamo.project.JaCaMoProject`; `JaCaMoProjectParser.parse` | `src/main/javacc/JaCaMoProjectParser.jj:170-216` | SOURCE |
| E02 | JCM parser creates agent parameter object | `jacamo.project.JaCaMoAgentParameters` | `JaCaMoProjectParser.jj:221-242` | SOURCE |
| E03 | JCM parser stores source/initial beliefs/goals in parameters | `JaCaMoAgentParameters.addInitBel/addInitGoal` | `JaCaMoProjectParser.jj:247-273`; `JaCaMoAgentParameters.java:40-58` | SOURCE |
| E04 | JCM agent instances expand through copy | `JaCaMoAgentParameters.copy` | `JaCaMoProjectParser.jj:278-285`; `JaCaMoAgentParameters.java:60-72` | SOURCE |
| E05 | JCM artifact is `ClassParameters`, not Artifact | `jason.mas2j.ClassParameters`; `JaCaMoWorkspaceParameters.artifacts` | `JaCaMoProjectParser.jj:306-321`; `JaCaMoWorkspaceParameters.java:16-44` | SOURCE |
| E06 | JCM focus is raw tuple | `JaCaMoAgentParameters.focus` | `JaCaMoAgentParameters.java:26-29`, `107-124` | SOURCE |
| E07 | JCM role is raw tuple | `JaCaMoAgentParameters.roles` | `JaCaMoAgentParameters.java:26-29`, `89-101` | SOURCE |
| E08 | Parser normalization is `fix()` | `JaCaMoAgentParameters.fix` | `JaCaMoProject.java:191-195`; `JaCaMoAgentParameters.java:126-145` | SOURCE |
| E09 | Launch creates Cartago platform | `jacamo.platform.Cartago.init/start` | `Cartago.java:21-38` | SOURCE |
| E10 | Launch creates workspace and records `WorkspaceId` | `Cartago.start`; `JaCaMoWorkspaceParameters.wId` | `Cartago.java:34-65`; `JaCaMoWorkspaceParameters.java:16-22,31-32` | SOURCE |
| E11 | Launch creates runtime artifact from class parameters | `cartago.Workspace.makeArtifact` via `Cartago.start` | `Cartago.java:74-89` | SOURCE + BYTECODE |
| E12 | Jason parser adds initial beliefs/goals | `jason.asSyntax.parser.as2j.agent_component` | Jason source artifact `jason/asSyntax/parser/as2j.java:147-189` | SOURCE |
| E13 | Jason parser constructs plans | `as2j.plan`; `new Plan` | Jason source artifact `as2j.java:328-430`; `Plan.java:38-74` | SOURCE |
| E14 | Jason plan contains trigger/context/body | `jason.asSyntax.Plan` fields `tevent/context/body` | Jason source artifact `Plan.java:38-74,94-110,215-225` | SOURCE |
| E15 | Jason body is linked/mutable | `jason.asSyntax.PlanBodyImpl` | Jason source artifact `PlanBodyImpl.java:27-120`; `as2j.java:549-674` | SOURCE |
| E16 | Jason plan library mutates scope/indexes | `jason.pl.PlanLibrary.add` | Jason source artifact `PlanLibrary.java:29-69,113-205` | SOURCE |
| E17 | Jason source loads from URL/file/resource | `jason.asSemantics.Agent.loadAS/parseAS` | Jason source artifact `Agent.java:161-182,372-396` | SOURCE |
| E18 | Jason runtime owns TS/BB/PL | `jason.asSemantics.Agent` fields `bb/pl/ts` | Jason source artifact `Agent.java:50-65,117-136` | SOURCE |
| E19 | Jason architecture consumes JaCaMo parameters | `jacamo.infra.JaCaMoAgArch.init` | `JaCaMoAgArch.java:30-122` | SOURCE |
| E20 | CArtAgO annotations are runtime-retained | `cartago.OPERATION`, `cartago.GUARD` | `cartago-3.1.jar` `javap -v`; RuntimeVisibleAnnotations | BYTECODE |
| E21 | CArtAgO scans annotations and builds descriptors | `cartago.Artifact.setupOperations` | `cartago-3.1.jar` bytecode: `Class.getDeclaredMethods`, `isAnnotationPresent`, `ArtifactOpMethod`, `OpDescriptor` | BYTECODE |
| E22 | CArtAgO live artifact has protected id/context | `cartago.Artifact.getId/getCurrentOpAgentId` | `cartago-3.1.jar` `javap -p`; repository example calls `AuctionArtifact.java:38-43` | BYTECODE + SOURCE |
| E23 | CArtAgO artifact id includes UUID/workspace/creator/type | `cartago.ArtifactId` | `cartago-3.1.jar` `javap -p -s` | BYTECODE |
| E24 | CArtAgO observation has live and snapshot forms | `cartago.ObsProperty`, `cartago.ArtifactObsProperty`, `ArtifactInfo` | `cartago-3.1.jar` public signatures and fields | BYTECODE |
| E25 | Moise OS owns SS/FS/NS | `moise.os.OS` fields `ss/fs/ns` | Moise source `moise/os/OS.java:37-51,87-89` | SOURCE |
| E26 | Moise XML is schema-validated and DOM-loaded | `OS.loadOSFromURI`, `OS.setFromDOM` | Moise source `OS.java:143-214` | SOURCE |
| E27 | Moise role/group cardinality is owner-scoped | `moise.os.ss.Group.roles/subgroups` | Moise source `Group.java:33-44,122-143,296-306` | SOURCE |
| E28 | Moise scheme mission cardinality is scheme-scoped | `moise.os.fs.Scheme.missions` | Moise source `Scheme.java:32-38,106-124` | SOURCE |
| E29 | Moise TTF is string | `moise.os.fs.Goal.ttf` | Moise source `Goal.java:26-40,180-184` | SOURCE |
| E30 | Moise runtime OE stores agent/group/scheme maps | `moise.oe.OE.agents/groups/schs` | Moise source `OE.java:40-49` | SOURCE |
| E31 | Moise group instance links to group spec | `OE.addGroup`, `GroupInstance.spec` | Moise source `OE.java:244-290`; `GroupInstance.java:35-88` | SOURCE |
| E32 | Moise scheme instance materializes goals/plans | `SchemeInstance` constructor | Moise source `SchemeInstance.java:36-89` | SOURCE |
| E33 | Moise role player is created by adoption | `OEAgent.adoptRole` | Moise source `OEAgent.java:76-124` | SOURCE |
| E34 | Moise mission player is created by commitment | `OEAgent.commitToMission` | Moise source `OEAgent.java:329-374` | SOURCE |
| E35 | Moise goal instance tracks committed/achieved agents | `GoalInstance` fields/methods | Moise source `GoalInstance.java:33-52,138-244,329-344` | SOURCE |
| E36 | JaCaMo Moise startup uses board artifact operations | `jacamo.platform.Moise.createGroup/createScheme` | `Moise.java:22-93,104-179` | SOURCE |
| E37 | Hello JCM contains focus/workspace/org/group/scheme declarations | JCM case study | `doc/tutorials/hello-world/code/helloworld/helloworld.jcm:11-71` | SOURCE |
| E38 | Hello XML contains Moise specs/norms | Moise XML case study | `doc/tutorials/hello-world/code/helloworld/src/org/o1.xml:14-104` | SOURCE |
| E39 | Auction dynamically creates scheme and artifact | `auction_capabilities.asl` | `examples/auction/src/agt/auction_capabilities.asl:2-13,19-57` | SOURCE |
| E40 | Auction artifact operation/property model | `auction_env.AuctionArtifact` | `examples/auction/src/env/auction_env/AuctionArtifact.java:6-43` | SOURCE |
| E41 | House JCM only declares agents/instances | house project | `examples/house-building/house-building.jcm:7-19` | SOURCE |
| E42 | House runtime creates org/artifacts/scheme in ASL | `giacomo.asl` | `examples/house-building/src/agt/giacomo.asl:28-100` | SOURCE |
| E43 | House OS contains sequence/parallel plans and norms | `house-os.xml` | `examples/house-building/src/org/house-os.xml:14-124` | SOURCE |
| E44 | House artifact operation types are Java/CArtAgO | `tools.AuctionArt`, `simulator.House` | `examples/house-building/src/env/tools/AuctionArt.java:10-28`; `simulator/House.java:12-74` | SOURCE |

## Audit conclusion

The source-supported JaCaMo model is a layered system, not one uniform object graph:

```text
JCM parser parameters
  -> Jason launch/program/runtime objects
  -> CArtAgO workspace/artifact/observation runtime
  -> JaCaMo organization-board operations
  -> optional/direct Moise OS/OE objects depending on the execution path
```

The minimum faithful pre-transformation boundary is therefore:

1. preserve JCM parameter objects and raw reference tuples;
2. preserve Jason syntax/program objects separately from Jason cycle state;
3. preserve CArtAgO Java type metadata separately from live artifact ids/properties;
4. preserve Moise OS specification classes separately from OE instance classes;
5. preserve organization-board transport as a distinct JaCaMo runtime path;
6. label unobserved live state and semantic interpretation as `UNKNOWN`.

No USE/Ecore/plugin change is justified by this audit alone. Any subsequent transformation work must use this evidence boundary and must not promote the static source inventory into a claim of complete live semantic equivalence.

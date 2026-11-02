package org.jacamo.checkpoint.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative admission of source-owned, local producers. No case-name rules. */
public final class SourceAdmission {
    public static final String VERSION="LOCAL_SOURCE_ADMISSION/1.2";
    @FunctionalInterface public interface IncludeResolver {Path resolve(Path including,String reference)throws IOException;}
    public record Rejection(String source,String reason){ }
    public record Report(Map<String,String> hashes,List<Rejection> rejected) {
        public Report {hashes=Map.copyOf(hashes);rejected=List.copyOf(rejected);}
        public boolean supported(){return !hashes.isEmpty()&&rejected.isEmpty();}
        public void requireSupported(){if(!supported())throw new IllegalArgumentException("CHECKPOINT_SOURCE_UNSUPPORTED:"+rejected);}
    }
    private static final Map<String,Pattern> JAVA_UNSUPPORTED=Map.of(
            "UNMANAGED_THREAD_OR_EXECUTOR",Pattern.compile("\\b(Thread|ThreadGroup|Timer|TimerTask|Executors|ExecutorService|ScheduledExecutorService|ForkJoinPool|CompletableFuture)\\b"),
            "UNMANAGED_CLOCK",Pattern.compile("\\bSystem\\s*\\.\\s*(currentTimeMillis|nanoTime)\\s*\\(|\\b(LocalDateTime|LocalTime|LocalDate|OffsetDateTime|ZonedDateTime|Instant|Clock|Calendar|GregorianCalendar)\\b|\\bnew\\s+(?:java\\.util\\.)?Date\\s*\\("),
            "UNMANAGED_NATIVE_OR_REFLECTIVE_PRODUCER",Pattern.compile("\\b(native|Unsafe|ClassLoader)\\b|\\b(Class\\s*\\.\\s*forName|Runtime\\s*\\.\\s*getRuntime|ProcessBuilder)\\b|java\\.lang\\.reflect|java\\.net|java\\.nio\\.channels|org\\.jacamo\\.checkpoint"));
    private static final Pattern ASL_UNSUPPORTED=Pattern.compile("\\.(at|time|date|nano_time|create_agent|kill_agent|clone|fork|shell|exec|include)\\s*\\(");
    private static final Pattern ASL_ACTION=Pattern.compile("(?<![\\w$])\\.([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)\\s*(?=\\(|;|\\.)");
    private static final Set<String> SOURCE_CYCLE_ACTIONS=Set.of("print","println","concat","my_name","wait","fail","send","broadcast",
            "abolish","asserta","assertz","drop_event","drop_intention","succeed_goal","fail_goal","drop_all_events","drop_all_intentions",
            "member","length","nth","sort","reverse","findall","count","term2string","atom","number","string","literal","intend");
    private SourceAdmission(){ }
    /** Call on the frozen project source manifest before any platform component starts. */
    public static Report inspect(Path root,Collection<Path> files) throws IOException {
        return inspect(root,files,(file,reference)->file.getParent().resolve(reference).normalize(),Set.of());
    }
    public static Report inspect(Path root,Collection<Path> files,IncludeResolver resolver,Set<String> managedClockSources)throws IOException {
        Path realRoot=root.toRealPath();var hashes=new TreeMap<String,String>();var rejected=new ArrayList<Rejection>();
        rejected.addAll(GuiCallbackAdmission.inspect(realRoot,files));
        var packages=new HashSet<String>();
        for(Path selected:files)if(selected.toString().endsWith(".java")) {
            var declaration=Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(Files.readString(selected));
            if(declaration.find())packages.add(declaration.group(1)+".");
        }
        for(Path selected:files) {
            Path file=selected.toRealPath();
            if(!file.startsWith(realRoot)||!Files.isRegularFile(file)){rejected.add(new Rejection(selected.toString(),"SOURCE_OUTSIDE_ADMITTED_PROJECT"));continue;}
            String relative=realRoot.relativize(file).toString().replace('\\','/');byte[] bytes=Files.readAllBytes(file);
            try{hashes.put(relative,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));}
            catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
            String text=new String(bytes,StandardCharsets.UTF_8);
            // Built-in tool artifacts can own wall-clock/autonomous producers
            // even when a project contains no Java Thread/Timer declaration.
            if(Pattern.compile("\\bcartago\\.(tools|infrastructure)\\.(?!GUIArtifact\\b)").matcher(text).find())rejected.add(new Rejection(relative,"UNADMITTED_FRAMEWORK_TOOL_PRODUCER"));
            // Qualified-provider admission applies only to executable Java. XML/CSL/iStar
            // paths legitimately use dotted model keys such as schemeArguments.auction.Id;
            // they are verified by their native parsers and cannot instantiate a provider.
            if(relative.endsWith(".java")) {
                var qualified=Pattern.compile("\\b(?:[a-z][A-Za-z0-9_]*\\.)+[A-Z][A-Za-z0-9_$]*").matcher(text);
                while(qualified.find()) {
                    String type=qualified.group();
                    boolean pinned=type.startsWith("java.lang.")||type.startsWith("java.util.")||type.startsWith("java.io.")
                            ||type.startsWith("jason.asSyntax.")||type.startsWith("ora4mas.nopl.")||type.startsWith("moise.")
                            ||type.matches("cartago\\.[A-Z][A-Za-z0-9_$]*")||type.equals("cartago.tools.GUIArtifact")
                            ||type.startsWith("java.awt.")||type.startsWith("javax.swing.")
                            ||Set.of("org.jacamo.bridge.adapter.JaCaMoBridgePlatform","org.jacamo.bridge.adapter.BridgeAgArch","jacamo.platform.EnvironmentWebInspector",
                                "jacamo.infra.JaCaMoAgArch","jaca.CAgentArch","jason.bb.DefaultBeliefBase","jason.asSemantics.Agent").contains(type);
                    if(!pinned&&packages.stream().noneMatch(type::startsWith))rejected.add(new Rejection(relative,"UNADMITTED_QUALIFIED_PROVIDER:"+type));
                }
            }
            // Comments and literals are retained conservatively: uncertain source
            // capability is rejected rather than trusted because a regex missed it.
            if(relative.endsWith(".java"))JAVA_UNSUPPORTED.forEach((reason,pattern)->{
                String checked=reason.equals("UNMANAGED_CLOCK")&&managedClockSources.contains(relative)?text.replaceAll("\\b(?:java\\.lang\\.)?System\\s*\\.\\s*(currentTimeMillis|nanoTime)\\s*\\(","managedClock("):text;
                if(pattern.matcher(checked).find())rejected.add(new Rejection(relative,reason));
            });
            if(relative.endsWith(".java")) {
                var imports=Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;").matcher(text);
                while(imports.find()) {
                    String value=imports.group(1);
                    if(!value.startsWith("java.lang.")&&!value.startsWith("java.util.")&&!value.startsWith("java.io.")
                            &&!value.startsWith("cartago.")&&!value.startsWith("jason.asSyntax.")&&!value.startsWith("java.awt.")&&!value.startsWith("javax.swing.")&&packages.stream().noneMatch(value::startsWith))
                        rejected.add(new Rejection(relative,"UNADMITTED_EXTERNAL_PROVIDER:"+value));
                }
            }
            if(relative.endsWith(".asl")&&ASL_UNSUPPORTED.matcher(text).find())rejected.add(new Rejection(relative,"UNSUPPORTED_ASYNCHRONOUS_OR_CLOCK_INTERNAL_ACTION"));
            if(relative.endsWith(".asl")) {
                var actions=ASL_ACTION.matcher(text);
                while(actions.find())if(!SOURCE_CYCLE_ACTIONS.contains(actions.group(1)))rejected.add(new Rejection(relative,"UNADMITTED_INTERNAL_ACTION:"+actions.group(1)));
                var includes=Pattern.compile("\\binclude\\s*\\(\\s*\"([^\"]+)\"").matcher(text);
                while(includes.find()) {
                    String value=includes.group(1);
                    boolean framework=Set.of("$jacamo/templates/common-cartago.asl","$jacamo/templates/common-moise.asl","$moise/asl/org-obedient.asl").contains(value);
                    if(!framework) {
                        Path included=resolver.resolve(file,value);
                        if(included==null||!Files.isRegularFile(included)||!included.toRealPath().startsWith(realRoot)
                                ||files.stream().noneMatch(p->p.toAbsolutePath().normalize().equals(included.toAbsolutePath().normalize())))rejected.add(new Rejection(relative,"UNADMITTED_ASL_INCLUDE:"+value));
                    }
                }
            }
        }
        return new Report(hashes,rejected);
    }
}

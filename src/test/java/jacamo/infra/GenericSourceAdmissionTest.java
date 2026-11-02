package jacamo.infra;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jacamo.checkpoint.runtime.*;

public class GenericSourceAdmissionTest {
 @Test public void bootResolvesTheActualDefaultJasonRelativeFileSearchPaths()throws Exception{
  Path root=Files.createTempDirectory("generic-loader-path-"),nested=Files.createDirectories(root.resolve("src/agt/inc"));
  Path actor=Files.writeString(root.resolve("src/agt/agent.asl"),"{ include(\"reused.asl\") }"),shared=Files.writeString(nested.resolve("reused.asl"),"known.");
  var project=new jacamo.project.parser.JaCaMoProjectParser(new java.io.StringReader("mas generic { }")).parse(root.toString());
  var resolve=jacamo.checkpoint.CheckpointBoot.class.getDeclaredMethod("resolveInclude",jacamo.project.JaCaMoProject.class,Path.class,Path.class,String.class);resolve.setAccessible(true);
  assertEquals(shared.toRealPath(),resolve.invoke(null,project,root,actor,"reused.asl"));
 }
 @Test public void declaredIncludePathsAndReadOnlyCycleQueryAreAdmittedButEscapesAreNot()throws Exception{
  Path root=Files.createTempDirectory("generic-include-"),nested=Files.createDirectories(root.resolve("logic/shared"));
  Path caller=Files.writeString(root.resolve("actor.asl"),"{ include(\"shared.asl\") }\n+!work : .intend(work) <- .print(ok)."),included=Files.writeString(nested.resolve("shared.asl"),"known(value).");
  var files=List.of(caller,included);
  assertFalse(SourceAdmission.inspect(root,files).supported());
  var positive=SourceAdmission.inspect(root,files,(file,ref)->nested.resolve(ref),Set.of());assertTrue(positive.toString(),positive.supported());
  Path outside=Files.createTempFile("unadmitted-include-",".asl");Files.writeString(outside,"outside.");
  assertFalse(SourceAdmission.inspect(root,files,(file,ref)->outside,Set.of()).supported());
 }
 @Test public void guiPaintingIsTypedAndUnmanagedCallbacksAndTimersRemainRejected()throws Exception{
  Path root=Files.createTempDirectory("generic-display-");Path source=root.resolve("Canvas.java");
  String paint="import javax.swing.JTextArea; import javax.swing.SwingUtilities; class Canvas { JTextArea output; void show(String value){SwingUtilities.invokeLater(new Runnable(){public void run(){output.append(value);}});} }";
  Files.writeString(source,paint);assertTrue(SourceAdmission.inspect(root,List.of(source)).toString(),SourceAdmission.inspect(root,List.of(source)).supported());
  Files.writeString(source,paint.replace("output.append(value)","mutateDomain()"));assertFalse(SourceAdmission.inspect(root,List.of(source)).supported());
  Files.writeString(source,"class Canvas { void start(){new javax.swing.Timer(50,event -> {}).start();} }");assertFalse(SourceAdmission.inspect(root,List.of(source)).supported());
 }
 @Test public void declarativeDottedModelKeysDoNotBecomeJavaProviders()throws Exception{
  Path root=Files.createTempDirectory("generic-declarative-key-");
  Path mapping=Files.writeString(root.resolve("mapping.xml"),"<mapping key='schemeArguments.auction.Id'/>");
  assertTrue(SourceAdmission.inspect(root,List.of(mapping)).toString(),SourceAdmission.inspect(root,List.of(mapping)).supported());
  Path source=Files.writeString(root.resolve("Unknown.java"),"class Unknown { void run(){ external.Provider.call(); } }");
  assertFalse(SourceAdmission.inspect(root,List.of(source)).supported());
 }
 @Test public void compiledSystemClocksAreReboundAndFreezeWithTheActualSharedBarrier()throws Exception{
  Path root=Files.createTempDirectory("generic-clock-"),classes=Files.createDirectories(root.resolve("build/classes/java/main"));
  Path source=Files.writeString(root.resolve("TimeReading.java"),"public class TimeReading {public static long wall(){return System.currentTimeMillis();} public static long nanos(){return System.nanoTime();}}");
  assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","-d",classes.toString(),source.toString()));
  var entries=new LinkedHashSet<String>();entries.add(classes.toString());
  for(Class<?> type:List.of(GenericSourceAdmissionTest.class,SourceClockWeaver.class,org.objectweb.asm.ClassReader.class,org.junit.Assert.class))entries.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
  var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",String.join(java.io.File.pathSeparator,entries),GenericSourceAdmissionTest.ClockChild.class.getName(),root.toString()).redirectErrorStream(true).start();
  assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));String output=new String(process.getInputStream().readAllBytes());assertEquals(output,0,process.exitValue());
 }
 public static class ClockChild {
  public static void main(String[] args)throws Exception{
   Path root=Path.of(args[0]);var proof=SourceClockWeaver.prepare(root,List.of(root.resolve("TimeReading.java")));assertEquals(2,proof.sites());assertFalse(proof.classes().isEmpty());
   assertTrue(SourceAdmission.inspect(root,List.of(root.resolve("TimeReading.java")),(file,ref)->null,proof.sources()).supported());
   ExecutionBarrier.enable("logical-clock-proof");Class<?> reading=Class.forName("TimeReading");var wall=reading.getMethod("wall");var nanos=reading.getMethod("nanos");
   long epoch=ExecutionBarrier.closeAdmission();ExecutionBarrier.awaitFrozen(epoch,Duration.ofSeconds(2));long a=(long)wall.invoke(null),b=(long)nanos.invoke(null);
   Thread.sleep(180);assertEquals(a,(long)wall.invoke(null));assertEquals(b,(long)nanos.invoke(null));ExecutionBarrier.release(epoch);Thread.sleep(40);assertTrue((long)wall.invoke(null)>a);ExecutionBarrier.stop();
   System.out.println("MANAGED_PROJECT_SYSTEM_CLOCK_FREEZE_PASS");
  }
 }
}

import cartago.*;
import cartago.tools.GUIArtifact;
import cartago.util.agent.CartagoBasicContext;
import javax.swing.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;

/** Pinned GUIArtifact queues events while held; domain dispatch and await resumption use the barrier. */
public class GenericGuiBoundaryProbe {
 public static class DisplayArtifact extends GUIArtifact {
  static volatile DisplayArtifact instance;static CountDownLatch entered=new CountDownLatch(1);
  static AtomicInteger settled=new AtomicInteger(),events=new AtomicInteger();JButton button;
  public void init(){instance=this;button=new JButton("event");super.init();linkActionEventToOp(button,"clicked");}
  @OPERATION public void settle(){entered.countDown();await_time(600);settled.incrementAndGet();}
  @INTERNAL_OPERATION void clicked(java.awt.event.ActionEvent event){events.incrementAndGet();}
 }
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  ExecutionBarrier.enable("gui-boundary-probe");var workers=Executors.newSingleThreadExecutor();
  try {
   CartagoEnvironment.getInstance().init();var context=new CartagoBasicContext("gui-probe");
   var artifact=context.makeArtifact(context.getJoinedWspId("main"),"display",DisplayArtifact.class.getName());
   var operation=workers.submit(()->{try{context.doAction(artifact,new Op("settle"));}catch(Exception failure){throw new RuntimeException(failure);}});
   check(DisplayArtifact.entered.await(2,TimeUnit.SECONDS),"operation admitted before hold");
   long epoch=ExecutionBarrier.closeAdmission();ExecutionBarrier.awaitFrozen(epoch,Duration.ofSeconds(3));long clock=ExecutionBarrier.nowMillis();
   SwingUtilities.invokeAndWait(()->DisplayArtifact.instance.button.doClick());Thread.sleep(800);
   check(ExecutionBarrier.nowMillis()==clock,"logical clock advanced during GUI hold");
   check(DisplayArtifact.events.get()==0&&DisplayArtifact.settled.get()==0&&!operation.isDone(),"GUI event or awaiting operation escaped hold");
   ExecutionBarrier.release(epoch);operation.get(3,TimeUnit.SECONDS);long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
   while(DisplayArtifact.events.get()==0&&System.nanoTime()<end)Thread.sleep(10);
   check(DisplayArtifact.events.get()==1&&DisplayArtifact.settled.get()==1,"queued event/await did not resume exactly once");
   System.out.println("GENERIC_GUI_EVENT_AND_INFLIGHT_LOGICAL_TIME_BOUNDARY_PASS");System.exit(0);
  }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{workers.shutdownNow();ExecutionBarrier.stop();}
 }
}

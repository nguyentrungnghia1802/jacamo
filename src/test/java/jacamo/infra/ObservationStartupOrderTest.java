package jacamo.infra;

import java.util.*;
import jacamo.platform.Platform;
import org.junit.Test;
import static org.junit.Assert.*;

public class ObservationStartupOrderTest {
    @Test public void failedObserverNegotiationPreventsEveryFollowingPlatformAndAgentStart() {
        var trace=new ArrayList<String>();
        var observer=new Platform(){@Override public void start(){trace.add("observer");}};
        var domain=new Platform(){@Override public void start(){trace.add("domain");}};
        var launcher=new JaCaMoLauncher(){
            @Override protected void afterPlatformStart(Platform platform){
                assertSame(observer,platform);trace.add("negotiation");throw new IllegalStateException("missing source plan");
            }
            @Override protected void startAgs(){trace.add("agents");}
        };
        launcher.getPlatforms().addAll(List.of(observer,domain));
        assertThrows(IllegalStateException.class,launcher::start);
        assertEquals(List.of("observer","negotiation"),trace);
    }
}

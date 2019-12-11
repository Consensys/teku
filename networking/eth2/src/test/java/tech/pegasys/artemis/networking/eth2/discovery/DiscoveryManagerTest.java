package tech.pegasys.artemis.networking.eth2.discovery;

import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.eth2.discovery.ProtocolManager.State;

public class DiscoveryManagerTest {

  @Test
  public void testDiscoveryMangerStartStop() throws ExecutionException, InterruptedException {
    DiscoveryManager dm = new DiscoveryManager();
    Assertions.assertEquals(dm.getState(), State.STOPPED);
    Assertions.assertTrue(dm.stop().isCompletedExceptionally());
    Assertions.assertEquals(dm.start().get(), State.RUNNING);
    Assertions.assertTrue(dm.start().isCompletedExceptionally());
    Assertions.assertEquals(dm.stop().get(), State.STOPPED);
  }
}

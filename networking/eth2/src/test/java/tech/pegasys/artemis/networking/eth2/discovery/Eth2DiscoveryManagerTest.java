package tech.pegasys.artemis.networking.eth2.discovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.eth2.discovery.Eth2DiscoveryManager.DiscoveryRequest;
import tech.pegasys.artemis.networking.eth2.discovery.ProtocolManager.State;

@SuppressWarnings("UnstableApiUsage")
class Eth2DiscoveryManagerTest {

  @Test
  void testDiscoveryMangerStartStop() throws ExecutionException, InterruptedException {
    Eth2DiscoveryManager dm = new Eth2DiscoveryManager();
    Assertions
        .assertEquals(dm.getState(), State.STOPPED, "Discovery did not start in state STOPPED");
    Assertions.assertTrue(dm.stop().isCompletedExceptionally(),
        "Discovery cannot be stopped when already in state STOPPED");
    Assertions.assertEquals(dm.start().get(), State.RUNNING,
        "Discovery failed to start from STOPPED to RUNNING");
    Assertions.assertTrue(dm.start().isCompletedExceptionally(),
        "Discovery cannot be started when already in state RUNNING");
    Assertions.assertEquals(dm.stop().get(), State.STOPPED,
        "Discovery failed to stop from RUNNING to STOPPED");
  }

  EventBus eventBus = new EventBus();
  private final Eth2DiscoveryManager discoveryManager = mock(Eth2DiscoveryManager.class);

  @Test
  void testEventBusRegistration() {
    DiscoveryRequest discoveryRequest = new Eth2DiscoveryManager.DiscoveryRequest(2);
    discoveryManager.setEventBus(eventBus);
    eventBus.register(discoveryManager);
    eventBus.post(discoveryRequest);
    verify(discoveryManager).onDiscoveryRequest(discoveryRequest);
  }
}

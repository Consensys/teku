/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.networking.eth2.discovery;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("UnstableApiUsage")
class Eth2DiscoveryManagerTest {

  @Test
  void testDiscoveryMangerStartStop() throws ExecutionException, InterruptedException {
    Eth2DiscoveryManager dm = new Eth2DiscoveryManager();
    Assertions.assertEquals(
        dm.getState(),
        Eth2DiscoveryManager.State.STOPPED,
        "Discovery did not start in state STOPPED");
    Assertions.assertTrue(
        dm.stop().isCompletedExceptionally(),
        "Discovery cannot be stopped when already in state STOPPED");
    Assertions.assertEquals(
        dm.start().get(),
        Eth2DiscoveryManager.State.RUNNING,
        "Discovery failed to start from STOPPED to RUNNING");
    Assertions.assertTrue(
        dm.start().isCompletedExceptionally(),
        "Discovery cannot be started when already in state RUNNING");
    Assertions.assertEquals(
        dm.stop().get(),
        Eth2DiscoveryManager.State.STOPPED,
        "Discovery failed to stop from RUNNING to STOPPED");
  }

  EventBus eventBus = new EventBus();
  private final Eth2DiscoveryManager discoveryManager = mock(Eth2DiscoveryManager.class);

  @Test
  void testEventBusRegistration() {
    DiscoveryRequest discoveryRequest = new DiscoveryRequest(2);
    discoveryManager.setEventBus(eventBus);
    eventBus.register(discoveryManager);
    eventBus.post(discoveryRequest);
    verify(discoveryManager).onDiscoveryRequest(new DiscoveryRequest(2));
  }
}

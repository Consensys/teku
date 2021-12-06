/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.BeaconNodeFacade;
import tech.pegasys.teku.TekuFacade;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;
import tech.pegasys.teku.services.beaconchain.BeaconChainController;
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFactory;

public class TekuConfigurationTest {

  @TempDir Path tempDir;

  @Test
  void beaconChainControllerFactory_useCustomFactory() {
    AtomicBoolean customMethodCalled = new AtomicBoolean();
    BeaconChainControllerFactory customControllerFactory =
        (serviceConfig, beaconConfig) ->
            new BeaconChainController(serviceConfig, beaconConfig) {
              @Override
              protected void initP2PNetwork() {
                super.initP2PNetwork();
                customMethodCalled.set(true);
              }
            };

    TekuConfiguration tekuConfiguration =
        TekuConfiguration.builder()
            .data(b -> b.dataBasePath(tempDir))
            .logging(b -> b.destination(LoggingDestination.CONSOLE))
            .beaconChainControllerFactory(customControllerFactory)
            .build();

    BeaconNodeFacade beaconNode = TekuFacade.startBeaconNode(tekuConfiguration);

    assertThat(beaconNode).isNotNull();
    assertThat(customMethodCalled).isTrue();

    beaconNode.stop();
  }
}

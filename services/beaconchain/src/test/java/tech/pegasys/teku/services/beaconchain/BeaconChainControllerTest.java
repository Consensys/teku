/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.services.beaconchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;
import tech.pegasys.teku.storage.store.MemKeyValueStore;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class BeaconChainControllerTest {

  private final ServiceConfig serviceConfig = mock(ServiceConfig.class);
  private final Eth2NetworkConfiguration eth2Config =
      Eth2NetworkConfiguration.builder(Eth2NetworkConfiguration.MAINNET).build();
  private final EventChannels eventChannels = mock(EventChannels.class);
  private P2PConfig p2pConfig = P2PConfig.builder().build();
  private final BeaconRestApiConfig restApiConfig = BeaconRestApiConfig.builder().build();
  private final LoggingConfig loggingConfig = LoggingConfig.builder().build();

  @TempDir public Path dataDir;

  @BeforeEach
  public void setup() {
    when(eventChannels.getPublisher(any())).thenReturn(mock(SlotEventsChannel.class));
    when(serviceConfig.getEventChannels()).thenReturn(eventChannels);
    when(serviceConfig.getDataDirLayout())
        .thenReturn(
            DataDirLayout.createFrom(
                DataConfig.builder().dataBasePath(dataDir).beaconDataPath(dataDir).build()));
  }

  private BeaconChainConfiguration beaconChainConfiguration() {
    return new BeaconChainConfiguration(
        eth2Config,
        WeakSubjectivityConfig.builder().build(),
        ValidatorConfig.builder().build(),
        InteropConfig.builder().build(),
        p2pConfig,
        restApiConfig,
        PowchainConfiguration.builder().build(),
        loggingConfig,
        StoreConfig.builder().build());
  }

  @Test
  void getP2pPrivateKeyBytes_generatedKeyTest() throws IOException {
    BeaconChainController controller =
        new BeaconChainController(serviceConfig, beaconChainConfiguration());

    MemKeyValueStore<String, Bytes> store = new MemKeyValueStore<>();

    // check that new key is generated
    Bytes generatedPK = controller.getP2pPrivateKeyBytes(store);
    assertThat(generatedPK).isNotNull();
    assertThat(generatedPK.size()).isGreaterThanOrEqualTo(32);

    // check the same key loaded next time
    Bytes loadedPK = controller.getP2pPrivateKeyBytes(store);
    assertThat(loadedPK).isEqualTo(generatedPK);

    store = new MemKeyValueStore<>();

    // check that another key is generated after old key is deleted
    Bytes generatedAnotherPK = controller.getP2pPrivateKeyBytes(store);
    assertThat(generatedAnotherPK).isNotNull();
    assertThat(generatedAnotherPK.size()).isGreaterThanOrEqualTo(32);
    assertThat(generatedAnotherPK).isNotEqualTo(generatedPK);

    // check that user supplied private key file has precedence over generated file
    Path customPKFile = dataDir.resolve("customPK.hex");
    Files.writeString(customPKFile, generatedPK.toHexString());

    p2pConfig = P2PConfig.builder().p2pPrivateKeyFile(customPKFile.toString()).build();
    BeaconChainController controller1 =
        new BeaconChainController(serviceConfig, beaconChainConfiguration());
    Bytes customPK = controller1.getP2pPrivateKeyBytes(store);
    assertThat(customPK).isEqualTo(generatedPK);
  }
}

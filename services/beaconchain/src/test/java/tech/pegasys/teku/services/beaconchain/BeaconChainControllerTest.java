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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.storage.store.MemKeyValueStore;
import tech.pegasys.teku.util.config.TekuConfiguration;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class BeaconChainControllerTest {

  @TempDir public File dataDir;

  private ServiceConfig mockServiceConfig(TekuConfiguration tekuConfiguration) {
    ServiceConfig serviceConfig = mock(ServiceConfig.class);
    when(serviceConfig.getConfig()).thenReturn(tekuConfiguration);
    EventChannels eventChannels = mock(EventChannels.class);
    when(eventChannels.getPublisher(any())).thenReturn(mock(SlotEventsChannel.class));
    when(serviceConfig.getEventChannels()).thenReturn(eventChannels);
    return serviceConfig;
  }

  @Test
  void getP2pPrivateKeyBytes_generatedKeyTest() throws IOException {
    TekuConfiguration tekuConfiguration =
        TekuConfiguration.builder().setDataPath(dataDir.getCanonicalPath()).build();
    BeaconChainController controller =
        new BeaconChainController(mockServiceConfig(tekuConfiguration));

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
    Path customPKFile = dataDir.toPath().resolve("customPK.hex");
    Files.writeString(customPKFile, generatedPK.toHexString());

    TekuConfiguration tekuConfiguration1 =
        TekuConfiguration.builder()
            .setDataPath(dataDir.getCanonicalPath())
            .setP2pPrivateKeyFile(customPKFile.toString())
            .build();
    BeaconChainController controller1 =
        new BeaconChainController(mockServiceConfig(tekuConfiguration1));
    Bytes customPK = controller1.getP2pPrivateKeyBytes(store);
    assertThat(customPK).isEqualTo(generatedPK);
  }
}

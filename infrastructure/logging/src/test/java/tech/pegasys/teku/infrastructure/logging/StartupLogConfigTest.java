/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

public class StartupLogConfigTest {

  @Test
  void checkReport() {
    final HardwareAbstractionLayer hardwareInfo = mock(HardwareAbstractionLayer.class);
    final GlobalMemory memory = mock(GlobalMemory.class);
    when(hardwareInfo.getMemory()).thenReturn(memory);
    when(memory.getTotal()).thenReturn(Long.valueOf("17179869184"));

    final CentralProcessor centralProcessor = mock(CentralProcessor.class);
    when(hardwareInfo.getProcessor()).thenReturn(centralProcessor);
    when(centralProcessor.getLogicalProcessorCount()).thenReturn(10);

    final StartupLogConfig config =
        StartupLogConfig.builder()
            .network("mainnet")
            .storageMode("PRUNE")
            .hardwareInfo(hardwareInfo)
            .beaconChainRestApiInterface("127.0.0.1")
            .beaconChainRestApiPort(5678)
            .beaconChainRestApiAllow(List.of("127.0.0.1", "localhost"))
            .validatorRestApiInterface("127.0.0.1")
            .validatorRestApiPort(6789)
            .validatorRestApiAllow(List.of("127.0.0.1", "localhost"))
            .executionEngineEndpoint("http://localhost:6000/")
            .build();

    assertThat(config.getReport())
        .containsExactly(
            "Configuration | Network: mainnet, Storage Mode: PRUNE",
            "Host Configuration | Maximum Heap Size: 4.00 GB, Total Memory: 16.00 GB, CPU Cores: 10",
            "Rest Api Configuration | Listen Address: 127.0.0.1, Port: 5678, Allow: [127.0.0.1, localhost]",
            "Validator Api Configuration | Listen Address: 127.0.0.1, Port 6789, Allow: [127.0.0.1, localhost]",
            "Execution Layer Configuration | Execution Endpoint: http://localhost:6000/");
  }
}

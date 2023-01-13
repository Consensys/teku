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

import java.util.List;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

public class StartupLogConfig {
  private final String network;
  private final String storageMode;
  private final int restApiPort;

  private final String maxHeapSize;
  private final String memory;
  private final int cpuCores;

  public StartupLogConfig(final String network, final String storageMode, final int restApiPort) {
    this.network = network;
    this.storageMode = storageMode;
    this.restApiPort = restApiPort;

    final HardwareAbstractionLayer hardwareInfo = new SystemInfo().getHardware();
    this.maxHeapSize = normalizeSize(Runtime.getRuntime().maxMemory());
    this.memory = normalizeSize(hardwareInfo.getMemory().getTotal());
    this.cpuCores = hardwareInfo.getProcessor().getLogicalProcessorCount();
  }

  private String normalizeSize(final long size) {
    return String.format("%.02f", (double) (size) / 1024 / 1024 / 1024) + " GB";
  }

  public List<String> getReport() { // TODO clean up formatting here
    final String general =
        String.format(
            "Configuration | Network: %s, Storage Mode: %s, Rest API Port: %s",
            network, storageMode, restApiPort);
    final String host =
        String.format(
            "Host Configuration | Maximum Heap Size: %s, Total Memory: %s, CPU Cores: %d",
            maxHeapSize, memory, cpuCores);
    return List.of(general, host);
  }
}

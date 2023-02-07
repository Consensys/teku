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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import oshi.hardware.HardwareAbstractionLayer;

public class StartupLogConfig {
  private final String network;
  private final String storageMode;

  private final String maxHeapSize;
  private final String memory;
  private final int cpuCores;

  private final boolean beaconChainRestApiEnabled;
  private final String beaconChainRestApiInterface;
  private final int beaconChainRestApiPort;
  private final List<String> beaconChainRestApiAllow;

  private final String validatorRestApiInterface;
  private final int validatorRestApiPort;
  private final List<String> validatorRestApiAllow;

  public StartupLogConfig(
      final String network,
      final String storageMode,
      final HardwareAbstractionLayer hardwareInfo,
      final boolean beaconChainRestApiEnabled,
      final String beaconChainRestApiInterface,
      final int beaconChainRestApiPort,
      final List<String> beaconChainRestApiAllow,
      final String validatorRestApiInterface,
      final int validatorRestApiPort,
      final List<String> validatorRestApiAllow) {
    this.network = network;
    this.storageMode = storageMode;

    this.maxHeapSize = normalizeSize(Runtime.getRuntime().maxMemory());
    this.memory = normalizeSize(hardwareInfo.getMemory().getTotal());
    this.cpuCores = hardwareInfo.getProcessor().getLogicalProcessorCount();

    this.beaconChainRestApiEnabled = beaconChainRestApiEnabled;
    this.beaconChainRestApiInterface = beaconChainRestApiInterface;
    this.beaconChainRestApiPort = beaconChainRestApiPort;
    this.beaconChainRestApiAllow = beaconChainRestApiAllow;

    this.validatorRestApiInterface = validatorRestApiInterface;
    this.validatorRestApiPort = validatorRestApiPort;
    this.validatorRestApiAllow = validatorRestApiAllow;
  }

  private String normalizeSize(final long size) {
    return String.format("%.02f", (double) size / 1024 / 1024 / 1024) + " GB";
  }

  public List<String> getReport() {
    final String general =
        String.format("Configuration | Network: %s, Storage Mode: %s", network, storageMode);
    final String host =
        String.format(
            "Host Configuration | Maximum Heap Size: %s, Total Memory: %s, CPU Cores: %d",
            maxHeapSize, memory, cpuCores);
    final String restApi =
        beaconChainRestApiEnabled
            ? String.format(
                "Rest Api Configuration | Enabled: true, Listen Address: %s, Port: %s, Allow: %s",
                beaconChainRestApiInterface, beaconChainRestApiPort, beaconChainRestApiAllow)
            : "Rest Api Configuration | Enabled: false";
    final String validatorApi =
        String.format(
            "Validator Api Configuration | Listen Address: %s, Port %s, Allow: %s",
            validatorRestApiInterface, validatorRestApiPort, validatorRestApiAllow);
    return List.of(general, host, restApi, validatorApi);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String network;
    private String storageMode;
    private HardwareAbstractionLayer hardwareInfo;
    private boolean beaconChainRestApiEnabled;
    private String beaconChainRestApiInterface;
    private int beaconChainRestApiPort;
    private List<String> beaconChainRestApiAllow;
    private String validatorRestApiInterface;
    private int validatorRestApiPort;
    private List<String> validatorRestApiAllow;

    private Builder() {}

    public StartupLogConfig build() {
      return new StartupLogConfig(
          network,
          storageMode,
          hardwareInfo,
          beaconChainRestApiEnabled,
          beaconChainRestApiInterface,
          beaconChainRestApiPort,
          beaconChainRestApiAllow,
          validatorRestApiInterface,
          validatorRestApiPort,
          validatorRestApiAllow);
    }

    public Builder network(String network) {
      checkNotNull(network);
      this.network = network;
      return this;
    }

    public Builder storageMode(String storageMode) {
      checkNotNull(storageMode);
      this.storageMode = storageMode;
      return this;
    }

    public Builder hardwareInfo(HardwareAbstractionLayer hardwareInfo) {
      checkNotNull(hardwareInfo);
      this.hardwareInfo = hardwareInfo;
      return this;
    }

    public Builder beaconChainRestApiEnabled(boolean beaconChainRestApiEnabled) {
      this.beaconChainRestApiEnabled = beaconChainRestApiEnabled;
      return this;
    }

    public Builder beaconChainRestApiInterface(String beaconChainRestApiInterface) {
      checkNotNull(beaconChainRestApiInterface);
      this.beaconChainRestApiInterface = beaconChainRestApiInterface;
      return this;
    }

    public Builder beaconChainRestApiPort(int beaconChainRestApiPort) {
      this.beaconChainRestApiPort = beaconChainRestApiPort;
      return this;
    }

    public Builder beaconChainRestApiAllow(List<String> beaconChainRestApiAllow) {
      checkNotNull(beaconChainRestApiAllow);
      this.beaconChainRestApiAllow = beaconChainRestApiAllow;
      return this;
    }

    public Builder validatorRestApiInterface(String validatorRestApiInterface) {
      checkNotNull(validatorRestApiInterface);
      this.validatorRestApiInterface = validatorRestApiInterface;
      return this;
    }

    public Builder validatorRestApiPort(int validatorRestApiPort) {
      this.validatorRestApiPort = validatorRestApiPort;
      return this;
    }

    public Builder validatorRestApiAllow(List<String> validatorRestApiAllow) {
      checkNotNull(validatorRestApiAllow);
      this.validatorRestApiAllow = validatorRestApiAllow;
      return this;
    }
  }
}

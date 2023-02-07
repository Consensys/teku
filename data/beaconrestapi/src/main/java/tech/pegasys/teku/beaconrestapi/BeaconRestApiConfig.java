/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beaconrestapi;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.PortAvailability;

public class BeaconRestApiConfig {
  public static final int DEFAULT_REST_API_PORT = 5051;
  public static final int DEFAULT_MAX_EVENT_QUEUE_SIZE = 250;
  public static final int DEFAULT_MAX_URL_LENGTH = 65535;
  public static final String DEFAULT_REST_API_INTERFACE = "127.0.0.1";
  public static final List<String> DEFAULT_REST_API_HOST_ALLOWLIST =
      List.of("127.0.0.1", "localhost");
  public static final List<String> DEFAULT_REST_API_CORS_ALLOWED_ORIGINS = new ArrayList<>();
  public static final boolean DEFAULT_BEACON_LIVENESS_TRACKING_ENABLED = false;
  public static final int DEFAULT_SUBSCRIBE_THREADS_COUNT = 1;

  // Beacon REST API
  private final int restApiPort;
  private final boolean restApiDocsEnabled;
  private final boolean restApiEnabled;
  private final boolean restApiLightClientEnabled;
  private final boolean beaconLivenessTrackingEnabled;
  private final String restApiInterface;
  private final List<String> restApiHostAllowlist;
  private final List<String> restApiCorsAllowedOrigins;
  private final Eth1Address eth1DepositContractAddress;
  private final int maxUrlLength;
  private final int maxPendingEvents;
  private final int validatorThreads;

  private BeaconRestApiConfig(
      final int restApiPort,
      final boolean restApiDocsEnabled,
      final boolean restApiEnabled,
      final boolean restApiLightClientEnabled,
      final String restApiInterface,
      final List<String> restApiHostAllowlist,
      final List<String> restApiCorsAllowedOrigins,
      final Eth1Address eth1DepositContractAddress,
      final int maxUrlLength,
      final int maxPendingEvents,
      final int validatorThreads,
      final boolean beaconLivenessTrackingEnabled) {
    this.restApiPort = restApiPort;
    this.restApiDocsEnabled = restApiDocsEnabled;
    this.restApiEnabled = restApiEnabled;
    this.restApiLightClientEnabled = restApiLightClientEnabled;
    this.restApiInterface = restApiInterface;
    this.restApiHostAllowlist = restApiHostAllowlist;
    this.restApiCorsAllowedOrigins = restApiCorsAllowedOrigins;
    this.eth1DepositContractAddress = eth1DepositContractAddress;
    this.maxUrlLength = maxUrlLength;
    this.maxPendingEvents = maxPendingEvents;
    this.validatorThreads = validatorThreads;
    this.beaconLivenessTrackingEnabled = beaconLivenessTrackingEnabled;
  }

  public int getRestApiPort() {
    return restApiPort;
  }

  public boolean isRestApiDocsEnabled() {
    return restApiDocsEnabled;
  }

  public boolean isRestApiEnabled() {
    return restApiEnabled;
  }

  public boolean isRestApiLightClientEnabled() {
    return restApiLightClientEnabled;
  }

  public boolean isBeaconLivenessTrackingEnabled() {
    return beaconLivenessTrackingEnabled;
  }

  public String getRestApiInterface() {
    return restApiInterface;
  }

  public List<String> getRestApiHostAllowlist() {
    return restApiHostAllowlist;
  }

  public List<String> getRestApiCorsAllowedOrigins() {
    return restApiCorsAllowedOrigins;
  }

  public Eth1Address getEth1DepositContractAddress() {
    return eth1DepositContractAddress;
  }

  public int getMaxPendingEvents() {
    return maxPendingEvents;
  }

  public int getMaxUrlLength() {
    return maxUrlLength;
  }

  public int getValidatorThreads() {
    return validatorThreads;
  }

  public static BeaconRestApiConfigBuilder builder() {
    return new BeaconRestApiConfigBuilder();
  }

  public static final class BeaconRestApiConfigBuilder {
    // Beacon REST API
    private int restApiPort = DEFAULT_REST_API_PORT;
    private boolean restApiDocsEnabled = false;
    private boolean restApiEnabled = false;
    private boolean restApiLightClientEnabled = false;
    private boolean beaconLivenessTrackingEnabled = DEFAULT_BEACON_LIVENESS_TRACKING_ENABLED;
    private String restApiInterface = DEFAULT_REST_API_INTERFACE;
    private List<String> restApiHostAllowlist = DEFAULT_REST_API_HOST_ALLOWLIST;
    private List<String> restApiCorsAllowedOrigins = DEFAULT_REST_API_CORS_ALLOWED_ORIGINS;
    private int maxPendingEvents = DEFAULT_MAX_EVENT_QUEUE_SIZE;
    private int maxUrlLength = DEFAULT_MAX_URL_LENGTH;
    private int validatorThreads = DEFAULT_SUBSCRIBE_THREADS_COUNT;
    private Eth1Address eth1DepositContractAddress;

    private BeaconRestApiConfigBuilder() {}

    public BeaconRestApiConfigBuilder restApiPort(final int restApiPort) {
      if (!PortAvailability.isPortValid(restApiPort)) {
        throw new InvalidConfigurationException(
            String.format("Invalid restApiPort: %d", restApiPort));
      }
      this.restApiPort = restApiPort;
      return this;
    }

    public BeaconRestApiConfigBuilder restApiDocsEnabled(final boolean restApiDocsEnabled) {
      this.restApiDocsEnabled = restApiDocsEnabled;
      return this;
    }

    public BeaconRestApiConfigBuilder restApiEnabled(final boolean restApiEnabled) {
      this.restApiEnabled = restApiEnabled;
      return this;
    }

    public BeaconRestApiConfigBuilder restApiLightClientEnabled(
        final boolean restApiLightClientEnabled) {
      this.restApiLightClientEnabled = restApiLightClientEnabled;
      return this;
    }

    public BeaconRestApiConfigBuilder restApiInterface(final String restApiInterface) {
      this.restApiInterface = restApiInterface;
      return this;
    }

    public BeaconRestApiConfigBuilder restApiHostAllowlist(
        final List<String> restApiHostAllowlist) {
      this.restApiHostAllowlist = restApiHostAllowlist;
      return this;
    }

    public BeaconRestApiConfigBuilder restApiCorsAllowedOrigins(
        final List<String> restApiCorsAllowedOrigins) {
      this.restApiCorsAllowedOrigins = restApiCorsAllowedOrigins;
      return this;
    }

    public BeaconRestApiConfigBuilder eth1DepositContractAddress(
        final Eth1Address eth1DepositContractAddress) {
      checkNotNull(eth1DepositContractAddress);
      this.eth1DepositContractAddress = eth1DepositContractAddress;
      return this;
    }

    public BeaconRestApiConfigBuilder eth1DepositContractAddressDefault(
        final Eth1Address eth1DepositContractAddress) {
      if (this.eth1DepositContractAddress == null) {
        this.eth1DepositContractAddress = eth1DepositContractAddress;
      }
      return this;
    }

    public BeaconRestApiConfigBuilder maxPendingEvents(final int maxEventQueueSize) {
      if (maxEventQueueSize < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid maxEventQueueSize: %d", maxEventQueueSize));
      }
      this.maxPendingEvents = maxEventQueueSize;
      return this;
    }

    public BeaconRestApiConfigBuilder beaconLivenessTrackingEnabled(
        final boolean beaconLivenessTrackingEnabled) {
      this.beaconLivenessTrackingEnabled = beaconLivenessTrackingEnabled;
      return this;
    }

    public BeaconRestApiConfigBuilder validatorThreads(final int validatorThreads) {
      // Generally this will be a low number, and there's a point where too many won't help.
      // 5000 seems like a lot of concurrent threads for a rest api
      // and at that point it's likely to not help if you go higher; so that can be a starting upper
      // sanity bound.
      if (validatorThreads < 1 || validatorThreads > 5_000) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid validatorThreads: %d should be between 1 and 5000", validatorThreads));
      }
      this.validatorThreads = validatorThreads;
      return this;
    }

    public BeaconRestApiConfig build() {
      return new BeaconRestApiConfig(
          restApiPort,
          restApiDocsEnabled,
          restApiEnabled,
          restApiLightClientEnabled,
          restApiInterface,
          restApiHostAllowlist,
          restApiCorsAllowedOrigins,
          eth1DepositContractAddress,
          maxUrlLength,
          maxPendingEvents,
          validatorThreads,
          beaconLivenessTrackingEnabled);
    }

    public BeaconRestApiConfigBuilder maxUrlLength(final int maxUrlLength) {
      this.maxUrlLength = maxUrlLength;
      return this;
    }
  }
}

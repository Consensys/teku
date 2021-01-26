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

package tech.pegasys.teku.networking.eth2;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;

public class P2PConfig {

  private final NetworkConfig networkConfig;
  private final DiscoveryConfig discoveryConfig;

  private final boolean multiPeerSyncEnabled;
  private final int targetSubnetSubscriberCount;
  private final boolean subscribeAllSubnetsEnabled;
  private final int peerRateLimit;
  private final int peerRequestLimit;

  private volatile Optional<Checkpoint> requiredCheckpoint;

  private P2PConfig(
      final NetworkConfig networkConfig,
      final DiscoveryConfig discoveryConfig,
      final boolean multiPeerSyncEnabled,
      final int targetSubnetSubscriberCount,
      final boolean subscribeAllSubnetsEnabled,
      Optional<Checkpoint> requiredCheckpoint,
      final int peerRateLimit,
      final int peerRequestLimit) {
    this.networkConfig = networkConfig;
    this.discoveryConfig = discoveryConfig;
    this.multiPeerSyncEnabled = multiPeerSyncEnabled;
    this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
    this.subscribeAllSubnetsEnabled = subscribeAllSubnetsEnabled;
    this.requiredCheckpoint = requiredCheckpoint;
    this.peerRateLimit = peerRateLimit;
    this.peerRequestLimit = peerRequestLimit;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Optional<Checkpoint> getRequiredCheckpoint() {
    return requiredCheckpoint;
  }

  public void setRequiredCheckpoint(final Checkpoint checkpoint) {
    checkNotNull(checkpoint);
    this.requiredCheckpoint = Optional.of(checkpoint);
  }

  public void clearRequiredCheckpoint() {
    this.requiredCheckpoint = Optional.empty();
  }

  public NetworkConfig getNetworkConfig() {
    return networkConfig;
  }

  public DiscoveryConfig getDiscoveryConfig() {
    return discoveryConfig;
  }

  public boolean isMultiPeerSyncEnabled() {
    return multiPeerSyncEnabled;
  }

  public int getTargetSubnetSubscriberCount() {
    return targetSubnetSubscriberCount;
  }

  public boolean isSubscribeAllSubnetsEnabled() {
    return subscribeAllSubnetsEnabled;
  }

  public int getPeerRateLimit() {
    return peerRateLimit;
  }

  public int getPeerRequestLimit() {
    return peerRequestLimit;
  }

  public static class Builder {
    public static final int DEFAULT_PEER_RATE_LIMIT = 500;
    public static final int DEFAULT_PEER_REQUEST_LIMIT = 50;

    private final NetworkConfig.Builder networkConfig = NetworkConfig.builder();
    private final DiscoveryConfig.Builder discoveryConfig = DiscoveryConfig.builder();

    private Boolean multiPeerSyncEnabled = false;
    private Integer targetSubnetSubscriberCount = 2;
    private Boolean subscribeAllSubnetsEnabled = false;
    private Optional<Checkpoint> requiredCheckpoint = Optional.empty();
    private Integer peerRateLimit = DEFAULT_PEER_RATE_LIMIT;
    private Integer peerRequestLimit = DEFAULT_PEER_REQUEST_LIMIT;

    private Builder() {}

    public P2PConfig build() {
      return new P2PConfig(
          networkConfig.build(),
          discoveryConfig.build(),
          multiPeerSyncEnabled,
          targetSubnetSubscriberCount,
          subscribeAllSubnetsEnabled,
          requiredCheckpoint,
          peerRateLimit,
          peerRequestLimit);
    }

    public Builder network(final Consumer<NetworkConfig.Builder> consumer) {
      consumer.accept(networkConfig);
      return this;
    }

    public Builder discovery(final Consumer<DiscoveryConfig.Builder> consumer) {
      consumer.accept(discoveryConfig);
      return this;
    }

    public Builder multiPeerSyncEnabled(final Boolean multiPeerSyncEnabled) {
      checkNotNull(multiPeerSyncEnabled);
      this.multiPeerSyncEnabled = multiPeerSyncEnabled;
      return this;
    }

    public Builder targetSubnetSubscriberCount(final Integer targetSubnetSubscriberCount) {
      checkNotNull(targetSubnetSubscriberCount);
      this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
      return this;
    }

    public Builder subscribeAllSubnetsEnabled(final Boolean subscribeAllSubnetsEnabled) {
      checkNotNull(subscribeAllSubnetsEnabled);
      this.subscribeAllSubnetsEnabled = subscribeAllSubnetsEnabled;
      return this;
    }

    public Builder requiredCheckpoint(final Optional<Checkpoint> requiredCheckpoint) {
      checkNotNull(requiredCheckpoint);
      this.requiredCheckpoint = requiredCheckpoint;
      return this;
    }

    public Builder peerRateLimit(final Integer peerRateLimit) {
      checkNotNull(peerRateLimit);
      this.peerRateLimit = peerRateLimit;
      return this;
    }

    public Builder peerRequestLimit(final Integer peerRequestLimit) {
      checkNotNull(peerRequestLimit);
      this.peerRequestLimit = peerRequestLimit;
      return this;
    }
  }
}

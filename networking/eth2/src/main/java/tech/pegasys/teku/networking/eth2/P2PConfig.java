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

package tech.pegasys.teku.networking.eth2;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.OptionalInt;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.networking.eth2.gossip.config.Eth2Context;
import tech.pegasys.teku.networking.eth2.gossip.config.GossipConfigurator;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.spec.Spec;

public class P2PConfig {

  public static final int DEFAULT_PEER_RATE_LIMIT = 500;
  public static final int DEFAULT_PEER_REQUEST_LIMIT = 50;
  public static final int DEFAULT_P2P_TARGET_SUBNET_SUBSCRIBER_COUNT = 2;
  public static final boolean DEFAULT_SUBSCRIBE_ALL_SUBNETS_ENABLED = false;
  public static final boolean DEFAULT_GOSSIP_SCORING_ENABLED = false;
  public static final int DEFAULT_BATCH_VERIFY_MAX_THREADS = 2;
  public static final int DEFAULT_BATCH_VERIFY_QUEUE_CAPACITY = 15_000;
  public static final int DEFAULT_BATCH_VERIFY_MAX_BATCH_SIZE = 250;
  public static final boolean DEFAULT_BATCH_VERIFY_STRICT_THREAD_LIMIT_ENABLED = false;

  private final Spec spec;
  private final NetworkConfig networkConfig;
  private final DiscoveryConfig discoveryConfig;
  private final GossipConfigurator gossipConfigurator;

  private final GossipEncoding gossipEncoding;
  private final int targetSubnetSubscriberCount;
  private final boolean subscribeAllSubnetsEnabled;
  private final int peerRateLimit;
  private final int peerRequestLimit;
  private final int batchVerifyMaxThreads;
  private final int batchVerifyQueueCapacity;
  private final int batchVerifyMaxBatchSize;
  private final boolean batchVerifyStrictThreadLimitEnabled;

  private P2PConfig(
      final Spec spec,
      final NetworkConfig networkConfig,
      final DiscoveryConfig discoveryConfig,
      final GossipConfigurator gossipConfigurator,
      final GossipEncoding gossipEncoding,
      final int targetSubnetSubscriberCount,
      final boolean subscribeAllSubnetsEnabled,
      final int peerRateLimit,
      final int peerRequestLimit,
      final int batchVerifyMaxThreads,
      final int batchVerifyQueueCapacity,
      final int batchVerifyMaxBatchSize,
      final boolean batchVerifyStrictThreadLimitEnabled) {
    this.spec = spec;
    this.networkConfig = networkConfig;
    this.discoveryConfig = discoveryConfig;
    this.gossipConfigurator = gossipConfigurator;
    this.gossipEncoding = gossipEncoding;
    this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
    this.subscribeAllSubnetsEnabled = subscribeAllSubnetsEnabled;
    this.peerRateLimit = peerRateLimit;
    this.peerRequestLimit = peerRequestLimit;
    this.batchVerifyMaxThreads = batchVerifyMaxThreads;
    this.batchVerifyQueueCapacity = batchVerifyQueueCapacity;
    this.batchVerifyMaxBatchSize = batchVerifyMaxBatchSize;
    this.batchVerifyStrictThreadLimitEnabled = batchVerifyStrictThreadLimitEnabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Spec getSpec() {
    return spec;
  }

  public NetworkConfig getNetworkConfig() {
    return networkConfig;
  }

  public DiscoveryConfig getDiscoveryConfig() {
    return discoveryConfig;
  }

  public GossipConfigurator getGossipConfigurator() {
    return gossipConfigurator;
  }

  public GossipEncoding getGossipEncoding() {
    return gossipEncoding;
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

  public int getBatchVerifyMaxThreads() {
    return batchVerifyMaxThreads;
  }

  public int getBatchVerifyMaxBatchSize() {
    return batchVerifyMaxBatchSize;
  }

  public int getBatchVerifyQueueCapacity() {
    return batchVerifyQueueCapacity;
  }

  public boolean isBatchVerifyStrictThreadLimitEnabled() {
    return batchVerifyStrictThreadLimitEnabled;
  }

  public static class Builder {
    private final NetworkConfig.Builder networkConfig = NetworkConfig.builder();
    private final DiscoveryConfig.Builder discoveryConfig = DiscoveryConfig.builder();

    private Spec spec;
    private Boolean isGossipScoringEnabled = DEFAULT_GOSSIP_SCORING_ENABLED;
    private GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    private Integer targetSubnetSubscriberCount = DEFAULT_P2P_TARGET_SUBNET_SUBSCRIBER_COUNT;
    private Boolean subscribeAllSubnetsEnabled = DEFAULT_SUBSCRIBE_ALL_SUBNETS_ENABLED;
    private Integer peerRateLimit = DEFAULT_PEER_RATE_LIMIT;
    private Integer peerRequestLimit = DEFAULT_PEER_REQUEST_LIMIT;
    private int batchVerifyMaxThreads = DEFAULT_BATCH_VERIFY_MAX_THREADS;
    private int batchVerifyQueueCapacity = DEFAULT_BATCH_VERIFY_QUEUE_CAPACITY;
    private int batchVerifyMaxBatchSize = DEFAULT_BATCH_VERIFY_MAX_BATCH_SIZE;
    private boolean batchVerifyStrictThreadLimitEnabled =
        DEFAULT_BATCH_VERIFY_STRICT_THREAD_LIMIT_ENABLED;

    private Builder() {}

    public P2PConfig build() {
      validate();

      final GossipConfigurator gossipConfigurator =
          isGossipScoringEnabled
              ? GossipConfigurator.scoringEnabled(spec)
              : GossipConfigurator.NOOP;
      final Eth2Context eth2Context =
          Eth2Context.builder()
              .activeValidatorCount(spec.getGenesisSpecConfig().getMinGenesisActiveValidatorCount())
              .gossipEncoding(gossipEncoding)
              .build();
      networkConfig.gossipConfig(c -> gossipConfigurator.configure(c, eth2Context));

      NetworkConfig networkConfig = this.networkConfig.build();
      discoveryConfig.listenUdpPortDefault(networkConfig.getListenPort());
      discoveryConfig.advertisedUdpPortDefault(OptionalInt.of(networkConfig.getAdvertisedPort()));

      return new P2PConfig(
          spec,
          networkConfig,
          discoveryConfig.build(),
          gossipConfigurator,
          gossipEncoding,
          targetSubnetSubscriberCount,
          subscribeAllSubnetsEnabled,
          peerRateLimit,
          peerRequestLimit,
          batchVerifyMaxThreads,
          batchVerifyQueueCapacity,
          batchVerifyMaxBatchSize,
          batchVerifyStrictThreadLimitEnabled);
    }

    private void validate() {
      checkNotNull(spec);
    }

    public Builder network(final Consumer<NetworkConfig.Builder> consumer) {
      consumer.accept(networkConfig);
      return this;
    }

    public Builder discovery(final Consumer<DiscoveryConfig.Builder> consumer) {
      consumer.accept(discoveryConfig);
      return this;
    }

    public Builder specProvider(final Spec spec) {
      checkNotNull(spec);
      this.spec = spec;
      return this;
    }

    public Builder isGossipScoringEnabled(final Boolean gossipScoringEnabled) {
      checkNotNull(gossipScoringEnabled);
      isGossipScoringEnabled = gossipScoringEnabled;
      return this;
    }

    public Builder targetSubnetSubscriberCount(final Integer targetSubnetSubscriberCount) {
      checkNotNull(targetSubnetSubscriberCount);
      if (targetSubnetSubscriberCount < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid targetSubnetSubscriberCount: %d", targetSubnetSubscriberCount));
      }
      this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
      return this;
    }

    public Builder subscribeAllSubnetsEnabled(final Boolean subscribeAllSubnetsEnabled) {
      checkNotNull(subscribeAllSubnetsEnabled);
      this.subscribeAllSubnetsEnabled = subscribeAllSubnetsEnabled;
      return this;
    }

    public Builder peerRateLimit(final Integer peerRateLimit) {
      checkNotNull(peerRateLimit);
      if (peerRateLimit < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid peerRateLimit: %d", peerRateLimit));
      }
      this.peerRateLimit = peerRateLimit;
      return this;
    }

    public Builder peerRequestLimit(final Integer peerRequestLimit) {
      checkNotNull(peerRequestLimit);
      if (peerRequestLimit < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid peerRequestLimit: %d", peerRequestLimit));
      }
      this.peerRequestLimit = peerRequestLimit;
      return this;
    }

    public Builder batchVerifyMaxThreads(final int batchVerifyMaxThreads) {
      if (batchVerifyMaxThreads < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid batchVerifyMaxThreads: %d", batchVerifyMaxThreads));
      }
      this.batchVerifyMaxThreads = batchVerifyMaxThreads;
      return this;
    }

    public Builder batchVerifyQueueCapacity(final int batchVerifyQueueCapacity) {
      if (batchVerifyQueueCapacity < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid batchVerifyQueueCapacity: %d", batchVerifyQueueCapacity));
      }
      this.batchVerifyQueueCapacity = batchVerifyQueueCapacity;
      return this;
    }

    public Builder batchVerifyMaxBatchSize(final int batchVerifyMaxBatchSize) {
      if (batchVerifyMaxBatchSize < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid batchVerifyMaxBatchSize: %d", batchVerifyMaxBatchSize));
      }
      this.batchVerifyMaxBatchSize = batchVerifyMaxBatchSize;
      return this;
    }

    public Builder batchVerifyStrictThreadLimitEnabled(
        final boolean batchVerifyStrictThreadLimitEnabled) {
      this.batchVerifyStrictThreadLimitEnabled = batchVerifyStrictThreadLimitEnabled;
      return this;
    }
  }
}

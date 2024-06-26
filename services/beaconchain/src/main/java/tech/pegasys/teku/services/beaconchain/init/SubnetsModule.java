/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.events.EventChannelSubscriber;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AllSubnetsSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AllSyncCommitteeSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.NodeBasedStableSubnetSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.StableSubnetSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubscriptionManager;
import tech.pegasys.teku.spec.Spec;

@Module
public interface SubnetsModule {

  @Provides
  @Singleton
  static AttestationTopicSubscriber attestationTopicSubscriber(
      @MetricsModule.SubnetSubscriptionsMetric SettableLabelledGauge subnetSubscriptionsMetric,
      Spec spec,
      P2PConfig p2pConfig,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      Eth2P2PNetwork p2pNetwork) {
    AttestationTopicSubscriber attestationTopicSubscriber =
        new AttestationTopicSubscriber(spec, p2pNetwork, subnetSubscriptionsMetric);
    if (p2pConfig.isSubscribeAllSubnetsEnabled()) {
      slotEventsChannelSubscriber.subscribe(attestationTopicSubscriber);
    }
    return attestationTopicSubscriber;
  }

  @Provides
  @Singleton
  static StableSubnetSubscriber stableSubnetSubscriber(
      Spec spec,
      P2PConfig p2pConfig,
      Eth2P2PNetwork p2pNetwork,
      AttestationTopicSubscriber attestationTopicSubscriber,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      LoggingModule.InitLogger logger) {
    final StableSubnetSubscriber stableSubnetSubscriber;
    if (p2pConfig.isSubscribeAllSubnetsEnabled()) {
      logger.logger().info("Subscribing to all attestation subnets");
      stableSubnetSubscriber =
          AllSubnetsSubscriber.create(attestationTopicSubscriber, spec.getNetworkingConfig());
    } else {
      if (p2pNetwork.getDiscoveryNodeId().isPresent()) {
        stableSubnetSubscriber =
            new NodeBasedStableSubnetSubscriber(
                attestationTopicSubscriber, spec, p2pNetwork.getDiscoveryNodeId().get());
      } else {
        logger
            .logger()
            .warn("Discovery nodeId is not defined, disabling stable subnet subscriptions");
        stableSubnetSubscriber = StableSubnetSubscriber.NOOP;
      }
    }
    slotEventsChannelSubscriber.subscribe(stableSubnetSubscriber);
    return stableSubnetSubscriber;
  }

  @Provides
  @Singleton
  static SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager(
      Spec spec,
      P2PConfig p2pConfig,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      Eth2P2PNetwork p2pNetwork) {

    final SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager;
    if (p2pConfig.isSubscribeAllSubnetsEnabled()) {
      syncCommitteeSubscriptionManager = new AllSyncCommitteeSubscriptions(p2pNetwork, spec);
      slotEventsChannelSubscriber.subscribe(syncCommitteeSubscriptionManager);
    } else {
      syncCommitteeSubscriptionManager = new SyncCommitteeSubscriptionManager(p2pNetwork);
    }
    return syncCommitteeSubscriptionManager;
  }
}

package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
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

import javax.inject.Singleton;

@Module
public interface SubnetsModule {

  @Provides
  @Singleton
  static AttestationTopicSubscriber provideAttestationTopicSubscriber(
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
  static SyncCommitteeSubscriptionManager provideSyncCommitteeSubscriptionManager(
      Spec spec,
      P2PConfig p2pConfig,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      Eth2P2PNetwork p2pNetwork) {
    SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager =
        p2pConfig.isSubscribeAllSubnetsEnabled()
            ? new AllSyncCommitteeSubscriptions(p2pNetwork, spec)
            : new SyncCommitteeSubscriptionManager(p2pNetwork);
    slotEventsChannelSubscriber.subscribe(syncCommitteeSubscriptionManager);
    return syncCommitteeSubscriptionManager;
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
      Spec spec, P2PConfig p2pConfig,
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

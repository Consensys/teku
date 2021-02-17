/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.gossip.config;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.util.DoubleAssert.assertThatDouble;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import java.time.Duration;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.AggregateGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.AttesterSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ProposerSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.VoluntaryExitGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.TopicNames;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipPeerScoringConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipScoringConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicScoringConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicsScoringConfig;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.StubSpecProvider;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class GossipScoringConfiguratorTest {

  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final Bytes4 forkDigest = Bytes4.fromHexString("0x01020304");
  private final SpecProvider specProvider = StubSpecProvider.createMainnet();
  private final int genesisMinActiveValidators =
      specProvider.getGenesisSpecConstants().getMinGenesisActiveValidatorCount();

  private GossipScoringConfigurator configurator;

  @BeforeEach
  public void setup() {
    configurator = new GossipScoringConfigurator(specProvider);
  }

  @Test
  public void configure_preGenesis() {
    final GossipConfig.Builder builder = GossipConfig.builder();
    configurator.configure(builder, preGenesisEth2Context());
    final GossipConfig config = builder.build();

    final GossipScoringConfig scoringConfig = config.getScoringConfig();
    assertThat(scoringConfig.getGossipThreshold()).isEqualTo(-4000);
    assertThat(scoringConfig.getPublishThreshold()).isEqualTo(-8000);
    assertThat(scoringConfig.getGraylistThreshold()).isEqualTo(-16000);
    assertThat(scoringConfig.getAcceptPXThreshold()).isEqualTo(100);
    assertThat(scoringConfig.getOpportunisticGraftThreshold()).isEqualTo(5);

    final GossipPeerScoringConfig peerScoring = scoringConfig.getPeerScoringConfig();

    assertThatDouble(peerScoring.getTopicScoreCap()).isApproximately(53.75);
    assertThatDouble(peerScoring.getIpColocationFactorWeight()).isApproximately(-53.75);
    assertThat(peerScoring.getIpColocationFactorThreshold()).isEqualTo(3);
    assertThatDouble(peerScoring.getBehaviourPenaltyDecay()).isApproximately(0.9857);
    assertThatDouble(peerScoring.getBehaviourPenaltyWeight()).isApproximately(-15.8793);
    assertThat(peerScoring.getBehaviourPenaltyThreshold()).isEqualTo(6.0);
    assertThat(peerScoring.getDecayToZero()).isEqualTo(0.01);
    assertThat(peerScoring.getDecayInterval().toSeconds()).isEqualTo(12);
    assertThat(peerScoring.getRetainScore().toSeconds()).isEqualTo(38400);

    // Check topics
    final Map<String, GossipTopicScoringConfig> topicsConfig =
        scoringConfig.getTopicScoringConfig();
    assertThat(topicsConfig).isEmpty();
  }

  @Test
  public void configureAllTopics_forMatureChain() {
    final GossipTopicsScoringConfig.Builder builder = GossipTopicsScoringConfig.builder();
    configurator.configureAllTopics(builder, postGenesisEth2Context());
    final Map<String, GossipTopicScoringConfig> allTopics = builder.build().getTopicConfigs();

    final int expectedCount = 5 + ATTESTATION_SUBNET_COUNT;
    assertThat(allTopics.size()).isEqualTo(expectedCount);

    validateVoluntaryExitTopicParams(allTopics);
    validateSlashingTopicParams(allTopics);
    validateAggregateTopicParams(allTopics, true);
    validateBlockTopicParams(allTopics, true);
    validateAllAttestationSubnetTopicParams(allTopics, true);
  }

  @Test
  public void configureAllTopics_atGenesis() {
    final GossipTopicsScoringConfig.Builder builder = GossipTopicsScoringConfig.builder();
    configurator.configureAllTopics(builder, genesisEth2Context());
    final Map<String, GossipTopicScoringConfig> allTopics = builder.build().getTopicConfigs();

    final int expectedCount = 5 + ATTESTATION_SUBNET_COUNT;
    assertThat(allTopics.size()).isEqualTo(expectedCount);

    validateVoluntaryExitTopicParams(allTopics);
    validateSlashingTopicParams(allTopics);
    validateAggregateTopicParams(allTopics, false);
    validateBlockTopicParams(allTopics, false);
    validateAllAttestationSubnetTopicParams(allTopics, false);
  }

  private void validateVoluntaryExitTopicParams(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final GossipTopicScoringConfig params = getVoluntaryExitTopicScoring(allTopics);
    assertMessageRatePenaltiesDisabled(params);

    assertThat(params.getTopicWeight()).isEqualTo(0.05);
    assertThatDouble(params.getTimeInMeshWeight()).isApproximately(0.03333);
    assertThat(params.getTimeInMeshQuantum()).isEqualTo(Duration.ofSeconds(12));
    assertThat(params.getTimeInMeshCap()).isEqualTo(300.0);
    assertThatDouble(params.getFirstMessageDeliveriesWeight()).isApproximately(1.8407);
    assertThatDouble(params.getFirstMessageDeliveriesDecay()).isApproximately(0.99856);
    assertThatDouble(params.getFirstMessageDeliveriesCap()).isApproximately(21.73035);
    assertThatDouble(params.getInvalidMessageDeliveriesWeight()).isApproximately(-2150.0);
    assertThatDouble(params.getInvalidMessageDeliveriesDecay()).isApproximately(0.99713);
  }

  private void validateSlashingTopicParams(final Map<String, GossipTopicScoringConfig> allTopics) {
    final GossipTopicScoringConfig attesterSlashingParams =
        getAttesterSlashingTopicScoring(allTopics);
    final GossipTopicScoringConfig proposerSlashingParams =
        getProposerSlashingTopicScoring(allTopics);

    validateSlashingTopicParams(attesterSlashingParams);
    validateSlashingTopicParams(proposerSlashingParams);
  }

  private void validateSlashingTopicParams(final GossipTopicScoringConfig params) {
    assertMessageRatePenaltiesDisabled(params);

    assertThat(params.getTopicWeight()).isEqualTo(0.05);
    assertThatDouble(params.getTimeInMeshWeight()).isApproximately(0.03333);
    assertThat(params.getTimeInMeshQuantum()).isEqualTo(Duration.ofSeconds(12));
    assertThat(params.getTimeInMeshCap()).isEqualTo(300.0);
    assertThatDouble(params.getFirstMessageDeliveriesWeight()).isApproximately(36.81486);
    assertThatDouble(params.getFirstMessageDeliveriesDecay()).isApproximately(0.998561);
    assertThatDouble(params.getFirstMessageDeliveriesCap()).isApproximately(1.08652);
    assertThatDouble(params.getInvalidMessageDeliveriesWeight()).isApproximately(-2150.0);
    assertThatDouble(params.getInvalidMessageDeliveriesDecay()).isApproximately(0.99713);
  }

  private void validateAggregateTopicParams(
      final Map<String, GossipTopicScoringConfig> allTopics, final boolean penaltiesActive) {
    final GossipTopicScoringConfig params = getAggregateTopicScoring(allTopics);

    assertThat(params.getTopicWeight()).isEqualTo(0.5);
    assertThatDouble(params.getTimeInMeshWeight()).isApproximately(0.03333);
    assertThat(params.getTimeInMeshQuantum()).isEqualTo(Duration.ofSeconds(12));
    assertThat(params.getTimeInMeshCap()).isEqualTo(300.0);
    assertThatDouble(params.getFirstMessageDeliveriesWeight()).isApproximately(0.33509);
    assertThatDouble(params.getFirstMessageDeliveriesDecay()).isApproximately(0.86596);
    assertThatDouble(params.getFirstMessageDeliveriesCap()).isApproximately(119.3712);
    assertThatDouble(params.getInvalidMessageDeliveriesWeight()).isApproximately(-215.0);
    assertThatDouble(params.getInvalidMessageDeliveriesDecay()).isApproximately(0.99713);

    // Check message rate penalty params
    assertThatDouble(params.getMeshMessageDeliveriesDecay()).isApproximately(0.930572);
    assertThatDouble(params.getMeshMessageDeliveriesCap()).isApproximately(68.6255);
    assertThat(params.getMeshMessageDeliveriesActivation()).isEqualTo(Duration.ofSeconds(384));
    assertThat(params.getMeshMessageDeliveryWindow()).isEqualTo(Duration.ofSeconds(2));
    assertThatDouble(params.getMeshFailurePenaltyWeight()).isApproximately(-0.73044);
    assertThatDouble(params.getMeshFailurePenaltyDecay()).isApproximately(0.93057);

    if (penaltiesActive) {
      assertThatDouble(params.getMeshMessageDeliveriesWeight()).isApproximately(-0.7304);
      assertThatDouble(params.getMeshMessageDeliveriesThreshold()).isApproximately(17.15638);
    } else {
      assertThat(params.getMeshMessageDeliveriesWeight()).isEqualTo(0.0);
      assertThat(params.getMeshMessageDeliveriesThreshold()).isEqualTo(0.0);
    }
  }

  private void validateAllAttestationSubnetTopicParams(
      final Map<String, GossipTopicScoringConfig> allTopics, final boolean penaltiesActive) {
    IntStream.range(0, ATTESTATION_SUBNET_COUNT)
        .mapToObj(subnet -> getAttestationSubnetTopicScoring(allTopics, subnet))
        .forEach(params -> validateAttestationSubnetTopicParams(params, penaltiesActive));
  }

  private void validateAttestationSubnetTopicParams(
      final GossipTopicScoringConfig params, final boolean penaltiesActive) {
    assertThat(params.getTopicWeight()).isEqualTo(0.015625);
    assertThatDouble(params.getTimeInMeshWeight()).isApproximately(0.03333);
    assertThat(params.getTimeInMeshQuantum()).isEqualTo(Duration.ofSeconds(12));
    assertThat(params.getTimeInMeshCap()).isEqualTo(300.0);
    assertThatDouble(params.getFirstMessageDeliveriesWeight()).isApproximately(2.6807);
    assertThatDouble(params.getFirstMessageDeliveriesDecay()).isApproximately(0.86596);
    assertThatDouble(params.getFirstMessageDeliveriesCap()).isApproximately(14.9214);
    assertThatDouble(params.getInvalidMessageDeliveriesWeight()).isApproximately(-6880.0);
    assertThatDouble(params.getInvalidMessageDeliveriesDecay()).isApproximately(0.99713);

    // Check message rate penalty params
    assertThatDouble(params.getMeshMessageDeliveriesDecay()).isApproximately(0.96466);
    assertThatDouble(params.getMeshMessageDeliveriesCap()).isApproximately(69.88248);
    assertThat(params.getMeshMessageDeliveriesActivation()).isEqualTo(Duration.ofSeconds(204));
    assertThat(params.getMeshMessageDeliveryWindow()).isEqualTo(Duration.ofSeconds(2));
    assertThatDouble(params.getMeshFailurePenaltyWeight()).isApproximately(-360.6548);
    assertThatDouble(params.getMeshFailurePenaltyDecay()).isApproximately(0.96466);

    if (penaltiesActive) {
      assertThatDouble(params.getMeshMessageDeliveriesWeight()).isApproximately(-360.6548);
      assertThatDouble(params.getMeshMessageDeliveriesThreshold()).isApproximately(4.367655);
    } else {
      assertThat(params.getMeshMessageDeliveriesWeight()).isEqualTo(0.0);
      assertThat(params.getMeshMessageDeliveriesThreshold()).isEqualTo(0.0);
    }
  }

  private void validateBlockTopicParams(
      final Map<String, GossipTopicScoringConfig> allTopics, final boolean penaltiesActive) {
    final GossipTopicScoringConfig blockTopicScoring = getBlockTopicScoring(allTopics);

    assertThat(blockTopicScoring.getTopicWeight()).isEqualTo(0.5);
    assertThatDouble(blockTopicScoring.getTimeInMeshWeight()).isApproximately(0.03333);
    assertThat(blockTopicScoring.getTimeInMeshQuantum()).isEqualTo(Duration.ofSeconds(12));
    assertThat(blockTopicScoring.getTimeInMeshCap()).isEqualTo(300.0);
    assertThatDouble(blockTopicScoring.getFirstMessageDeliveriesWeight()).isApproximately(1.14716);
    assertThatDouble(blockTopicScoring.getFirstMessageDeliveriesDecay()).isApproximately(0.99283);
    assertThatDouble(blockTopicScoring.getFirstMessageDeliveriesCap()).isApproximately(34.8687);
    assertThatDouble(blockTopicScoring.getInvalidMessageDeliveriesWeight()).isApproximately(-215.0);
    assertThatDouble(blockTopicScoring.getInvalidMessageDeliveriesDecay()).isApproximately(0.99713);

    // Check message rate penalty params
    assertThatDouble(blockTopicScoring.getMeshMessageDeliveriesDecay()).isApproximately(0.97163);
    assertThatDouble(blockTopicScoring.getMeshMessageDeliveriesCap()).isApproximately(2.0547574);
    assertThat(blockTopicScoring.getMeshMessageDeliveriesActivation())
        .isEqualTo(Duration.ofSeconds(384));
    assertThat(blockTopicScoring.getMeshMessageDeliveryWindow()).isEqualTo(Duration.ofSeconds(2));
    assertThatDouble(blockTopicScoring.getMeshFailurePenaltyWeight()).isApproximately(-458.31055);
    assertThatDouble(blockTopicScoring.getMeshFailurePenaltyDecay()).isApproximately(0.97163);

    if (penaltiesActive) {
      assertThatDouble(blockTopicScoring.getMeshMessageDeliveriesWeight())
          .isApproximately(-458.31055);
      assertThatDouble(blockTopicScoring.getMeshMessageDeliveriesThreshold())
          .isApproximately(0.68491);
    } else {
      assertThat(blockTopicScoring.getMeshMessageDeliveriesWeight()).isEqualTo(0.0);
      assertThat(blockTopicScoring.getMeshMessageDeliveriesThreshold()).isEqualTo(0.0);
    }
  }

  private void assertMessageRatePenaltiesDisabled(final GossipTopicScoringConfig scoringConfig) {
    assertThat(scoringConfig.getMeshMessageDeliveriesWeight()).isEqualTo(0.0);
    assertThat(scoringConfig.getMeshMessageDeliveriesDecay()).isEqualTo(0.0);
    assertThat(scoringConfig.getMeshMessageDeliveriesThreshold()).isEqualTo(0.0);
    assertThat(scoringConfig.getMeshMessageDeliveriesCap()).isEqualTo(0.0);
    assertThat(scoringConfig.getMeshFailurePenaltyWeight()).isEqualTo(0.0);
    assertThat(scoringConfig.getMeshFailurePenaltyDecay()).isEqualTo(0.0);
    assertThat(scoringConfig.getMeshMessageDeliveriesActivation().toSeconds()).isEqualTo(0);
    assertThat(scoringConfig.getMeshMessageDeliveryWindow().toSeconds()).isEqualTo(0);
  }

  private GossipTopicScoringConfig getBlockTopicScoring(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final String topic =
        TopicNames.getTopic(forkDigest, BlockGossipManager.TOPIC_NAME, gossipEncoding);
    return allTopics.get(topic);
  }

  private GossipTopicScoringConfig getVoluntaryExitTopicScoring(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final String topic =
        TopicNames.getTopic(forkDigest, VoluntaryExitGossipManager.TOPIC_NAME, gossipEncoding);
    return allTopics.get(topic);
  }

  private GossipTopicScoringConfig getProposerSlashingTopicScoring(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final String topic =
        TopicNames.getTopic(forkDigest, ProposerSlashingGossipManager.TOPIC_NAME, gossipEncoding);
    return allTopics.get(topic);
  }

  private GossipTopicScoringConfig getAttesterSlashingTopicScoring(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final String topic =
        TopicNames.getTopic(forkDigest, AttesterSlashingGossipManager.TOPIC_NAME, gossipEncoding);
    return allTopics.get(topic);
  }

  private GossipTopicScoringConfig getAggregateTopicScoring(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final String topic =
        TopicNames.getTopic(forkDigest, AggregateGossipManager.TOPIC_NAME, gossipEncoding);
    return allTopics.get(topic);
  }

  private GossipTopicScoringConfig getAttestationSubnetTopicScoring(
      final Map<String, GossipTopicScoringConfig> allTopics, final int subnet) {
    final String subnetName = TopicNames.getAttestationSubnetTopicName(subnet);
    final String topic = TopicNames.getTopic(forkDigest, subnetName, gossipEncoding);
    return allTopics.get(topic);
  }

  private Eth2Context preGenesisEth2Context() {
    return Eth2Context.builder()
        .activeValidatorCount(genesisMinActiveValidators)
        .gossipEncoding(GossipEncoding.SSZ_SNAPPY)
        .build();
  }

  private Eth2Context postGenesisEth2Context() {
    return Eth2Context.builder()
        .activeValidatorCount(genesisMinActiveValidators)
        .gossipEncoding(gossipEncoding)
        .forkDigest(forkDigest)
        .currentSlot(UInt64.valueOf(10_000))
        .build();
  }

  private Eth2Context genesisEth2Context() {
    return Eth2Context.builder()
        .activeValidatorCount(genesisMinActiveValidators)
        .gossipEncoding(gossipEncoding)
        .forkDigest(forkDigest)
        .currentSlot(UInt64.ZERO)
        .build();
  }
}

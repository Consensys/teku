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

package tech.pegasys.teku.test.acceptance;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

/** In order to cover all possible validator slashing scenarios, this acceptance test runs different combinations:
 * - Single Process: VC/BN running in a single process. In this case there is no SEE and the slashing is passed through the ValidatorTimingChannel
 * - Stand-Alone VC: VC/BN running in a separate processes and communicating through the REST APIs and SEE. In this case the slashing event is sent from the BN to the VC through SSE
 * - Single Peer: No network, the slashing event is directly received by the running node
 * - Multi Peers: Multiple running node, the slashing event is received by a first node through the PostAttesterSlashing or PostProposerSlashing REST APIs and then sent to the concerned node either through gossip or within a block
 * - No Blocks: the slashing event is not sent to the correspondent node within a block but rather through p2p gossip network
 * - No Gossip: the slashing event is not sent to the correspondent node through the p2p gossip network but rather within a block
 * */
public class ValidatorSlashingDetectionAcceptanceTest extends AcceptanceTestBase {

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();
  private final String network = "swift";
  private final String slashingActionLog =
      "Validator(s) with public key(s) %s got slashed. Shutting down...";

  private enum SlashingEventType {
    PROPOSER_SLASHING,
    ATTESTER_SLASHING;
  }

  public static Stream<Arguments> getSlashingEventTypes() {
    return Stream.of(SlashingEventType.values()).map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void shouldShutDownWhenOwnedValidatorSlashed_SingleProcess_SinglePeer(
      final SlashingEventType slashingEventType) throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuNode tekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withAltairEpoch(altairEpoch)
                    .withStopVcWhenValidatorSlashedEnabled()
                    .withInteropValidators(0, 32));

    tekuNode.start();

    tekuNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 3;
    final BLSKeyPair slashedValidatorKeyPair = getBlsKeyPair(slashedValidatorIndex);
    final int slotInFirstEpoch =
        tekuNode.getSpec().forMilestone(SpecMilestone.ALTAIR).getSlotsPerEpoch() - 1;

    postSlashing(
        tekuNode,
        UInt64.valueOf(slotInFirstEpoch),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        slashingEventType);

    tekuNode.waitForLogMessageContaining(
        String.format(slashingActionLog, slashedValidatorKeyPair.getPublicKey().toHexString()));
  }

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void shouldShutDownWhenOwnedValidatorSlashed_StandAloneVC_SinglePeer(
      final SlashingEventType slashingEventType) throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuNode beaconNode =
        createTekuNode(
            config -> configureNode(config, genesisTime, network).withAltairEpoch(altairEpoch));

    final TekuValidatorNode validatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("auto")
                    .withStopVcWhenValidatorSlashedEnabled()
                    .withInteropValidators(0, 32)
                    .withBeaconNode(beaconNode));

    beaconNode.start();
    validatorClient.start();

    beaconNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 3;
    final BLSKeyPair slashedValidatorKeyPair = getBlsKeyPair(slashedValidatorIndex);
    final int slotInFirstEpoch =
        beaconNode.getSpec().forMilestone(SpecMilestone.ALTAIR).getSlotsPerEpoch() - 1;

    postSlashing(
        beaconNode,
        UInt64.valueOf(slotInFirstEpoch),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        slashingEventType);

    validatorClient.waitForLogMessageContaining(
        String.format(slashingActionLog, slashedValidatorKeyPair.getPublicKey().toHexString()));
  }

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void shouldShutDownWhenOwnedValidatorSlashed_SingleProcess_MultiplePeers(
      final SlashingEventType slashingEventType) throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuNode firstTekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withRealNetwork()
                    .withAltairEpoch(altairEpoch)
                    .withInteropValidators(0, 32));

    firstTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(1);

    final TekuNode secondTekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withAltairEpoch(altairEpoch)
                    .withStopVcWhenValidatorSlashedEnabled()
                    .withInteropValidators(32, 32)
                    .withPeers(firstTekuNode));

    secondTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 34;
    final BLSKeyPair slashedValidatorKeyPair = getBlsKeyPair(slashedValidatorIndex);
    final int slotInFirstEpoch =
        firstTekuNode.getSpec().forMilestone(SpecMilestone.ALTAIR).getSlotsPerEpoch() - 1;

    postSlashing(
        firstTekuNode,
        UInt64.valueOf(slotInFirstEpoch),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        slashingEventType);

    secondTekuNode.waitForLogMessageContaining(
        String.format(slashingActionLog, slashedValidatorKeyPair.getPublicKey().toHexString()));

    firstTekuNode.stop();
  }

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void
      shouldShutDownWhenOwnedValidatorSlashed_SingleProcess_MultiplePeers_SlashingEventsThroughGossipOnly_NoBlocks(
          final SlashingEventType slashingEventType) throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuNode firstTekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withRealNetwork()
                    .withAltairEpoch(altairEpoch));

    firstTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(1);

    final TekuNode secondTekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withAltairEpoch(altairEpoch)
                    .withStopVcWhenValidatorSlashedEnabled()
                    .withInteropValidators(0, 32)
                    .withPeers(firstTekuNode));

    secondTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 4;
    final BLSKeyPair slashedValidatorKeyPair = getBlsKeyPair(slashedValidatorIndex);
    final int slotInFirstEpoch =
        firstTekuNode.getSpec().forMilestone(SpecMilestone.ALTAIR).getSlotsPerEpoch() - 1;

    postSlashing(
        firstTekuNode,
        UInt64.valueOf(slotInFirstEpoch),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        slashingEventType);

    secondTekuNode.waitForLogMessageContaining(
        String.format(slashingActionLog, slashedValidatorKeyPair.getPublicKey().toHexString()));

    firstTekuNode.stop();
  }

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void
      shouldShutDownWhenOwnedValidatorSlashed_SingleProcess_MultiplePeers_SlashingThroughBlock_NoSlashingEventsGossip(
          final SlashingEventType slashingEventType) throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuNode firstTekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withRealNetwork()
                    .withAltairEpoch(altairEpoch)
                    .withInteropValidators(0, 32));

    firstTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 34;
    final BLSKeyPair slashedValidatorKeyPair = getBlsKeyPair(slashedValidatorIndex);
    final int slotInFirstEpoch =
        firstTekuNode.getSpec().forMilestone(SpecMilestone.ALTAIR).getSlotsPerEpoch() - 1;

    postSlashing(
        firstTekuNode,
        UInt64.valueOf(slotInFirstEpoch),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        slashingEventType);

    final TekuNode secondTekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withAltairEpoch(altairEpoch)
                    .withStopVcWhenValidatorSlashedEnabled()
                    .withInteropValidators(32, 32)
                    .withPeers(firstTekuNode));

    secondTekuNode.start();

    secondTekuNode.waitForLogMessageContaining(
        String.format(slashingActionLog, slashedValidatorKeyPair.getPublicKey().toHexString()));

    firstTekuNode.stop();
  }

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void shouldShutDownWhenOwnedValidatorSlashed_StandAloneVC_MultiplePeers(
      final SlashingEventType slashingEventType) throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuNode firstTekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withRealNetwork()
                    .withAltairEpoch(altairEpoch)
                    .withInteropValidators(0, 32));

    firstTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(1);

    final TekuNode secondBeaconNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withRealNetwork()
                    .withAltairEpoch(altairEpoch)
                    .withPeers(firstTekuNode));

    final TekuValidatorNode secondValidatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("auto")
                    .withValidatorApiEnabled()
                    .withStopVcWhenValidatorSlashedEnabled()
                    .withInteropValidators(32, 32)
                    .withBeaconNode(secondBeaconNode));

    secondBeaconNode.start();

    secondValidatorClient.start();

    firstTekuNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 34;
    final BLSKeyPair slashedValidatorKeyPair = getBlsKeyPair(slashedValidatorIndex);
    final int slotInThirdEpoch =
        firstTekuNode.getSpec().forMilestone(SpecMilestone.ALTAIR).getSlotsPerEpoch() * 2 + 3;

    postSlashing(
        firstTekuNode,
        UInt64.valueOf(slotInThirdEpoch),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        slashingEventType);

    secondValidatorClient.waitForLogMessageContaining(
        String.format(slashingActionLog, slashedValidatorKeyPair.getPublicKey().toHexString()));

    secondBeaconNode.stop();
    secondValidatorClient.stop();
  }

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void
      shouldShutDownWhenOwnedValidatorSlashed_StandAloneVC_MultiplePeers_SlashingEventsThroughGossipOnly_NoBlocks(
          final SlashingEventType slashingEventType) throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuNode firstTekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withRealNetwork()
                    .withAltairEpoch(altairEpoch));

    firstTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(1);

    final TekuNode secondBeaconNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withRealNetwork()
                    .withAltairEpoch(altairEpoch)
                    .withPeers(firstTekuNode));

    final TekuValidatorNode secondValidatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("auto")
                    .withValidatorApiEnabled()
                    .withStopVcWhenValidatorSlashedEnabled()
                    .withInteropValidators(0, 32)
                    .withBeaconNode(secondBeaconNode));

    secondBeaconNode.start();

    secondValidatorClient.start();

    firstTekuNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 4;
    final BLSKeyPair slashedValidatorKeyPair = getBlsKeyPair(slashedValidatorIndex);
    final int slotInThirdEpoch =
        firstTekuNode.getSpec().forMilestone(SpecMilestone.ALTAIR).getSlotsPerEpoch() * 2 + 3;

    postSlashing(
        firstTekuNode,
        UInt64.valueOf(slotInThirdEpoch),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        slashingEventType);

    secondValidatorClient.waitForLogMessageContaining(
        String.format(slashingActionLog, slashedValidatorKeyPair.getPublicKey().toHexString()));

    secondBeaconNode.stop();
    secondValidatorClient.stop();
  }

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void
      shouldShutDownWhenOwnedValidatorSlashed_StandAloneVC_MultiplePeers_SlashingThroughBlock_NoSlashingEventsGossip(
          final SlashingEventType slashingEventType) throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuNode firstTekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withRealNetwork()
                    .withAltairEpoch(altairEpoch)
                    .withInteropValidators(0, 32));

    firstTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 34;
    final BLSKeyPair slashedValidatorKeyPair = getBlsKeyPair(slashedValidatorIndex);
    final int slotInThirdEpoch =
        firstTekuNode.getSpec().forMilestone(SpecMilestone.ALTAIR).getSlotsPerEpoch() * 2 + 3;

    postSlashing(
        firstTekuNode,
        UInt64.valueOf(slotInThirdEpoch),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        slashingEventType);

    final TekuNode secondBeaconNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime, network)
                    .withRealNetwork()
                    .withAltairEpoch(altairEpoch)
                    .withPeers(firstTekuNode));

    final TekuValidatorNode secondValidatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("auto")
                    .withValidatorApiEnabled()
                    .withStopVcWhenValidatorSlashedEnabled()
                    .withInteropValidators(32, 32)
                    .withBeaconNode(secondBeaconNode));

    secondBeaconNode.start();

    secondValidatorClient.start();

    secondValidatorClient.waitForLogMessageContaining(
        String.format(slashingActionLog, slashedValidatorKeyPair.getPublicKey().toHexString()));

    secondBeaconNode.stop();
    secondValidatorClient.stop();
  }

  private TekuNode.Config configureNode(
      final TekuNode.Config node, final int genesisTime, final String network) {
    return node.withNetwork(network).withGenesisTime(genesisTime).withRealNetwork();
  }

  private static BLSKeyPair getBlsKeyPair(int slashedValidatorIndex) {
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, slashedValidatorIndex + 1);
    return blsKeyPairs.get(slashedValidatorIndex);
  }

  private void postSlashing(
      final TekuNode tekuNode,
      final UInt64 slashingSlot,
      final UInt64 slashedIndex,
      final BLSSecretKey slashedValidatorSecretKey,
      final SlashingEventType slashingEventType)
      throws IOException {
    switch (slashingEventType) {
      case ATTESTER_SLASHING -> tekuNode.postAttesterSlashing(
          slashingSlot, slashedIndex, slashedValidatorSecretKey);
      case PROPOSER_SLASHING -> tekuNode.postProposerSlashing(
          slashingSlot, slashedIndex, slashedValidatorSecretKey);
    }
  }
}

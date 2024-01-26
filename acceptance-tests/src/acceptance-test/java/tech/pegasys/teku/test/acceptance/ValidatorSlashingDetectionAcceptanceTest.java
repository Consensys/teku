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
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class ValidatorSlashingDetectionAcceptanceTest extends AcceptanceTestBase {

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();
  private final String network = "swift";

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
                configureNode(config, genesisTime)
                    .withAltairEpoch(altairEpoch)
                    .withStopVcWhenValidatorSlashedEnabled()
                    .withInteropValidators(0, 32));

    tekuNode.start();

    tekuNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 3;
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, slashedValidatorIndex + 1);
    final BLSKeyPair slashedValidatorKeyPair = blsKeyPairs.get(slashedValidatorIndex);

    postSlashing(
        UInt64.valueOf(3),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        tekuNode,
        slashingEventType);

    tekuNode.waitForLogMessageContaining(
        String.format(
            "Validator(s) with public key(s) %s got slashed. Shutting down...",
            slashedValidatorKeyPair.getPublicKey().toHexString()));
  }

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void shouldShutDownWhenOwnedValidatorSlashed_StandAloneVC_SinglePeer(
      final SlashingEventType slashingEventType) throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuNode beaconNode =
        createTekuNode(config -> configureNode(config, genesisTime).withAltairEpoch(altairEpoch));

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
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, slashedValidatorIndex + 1);
    final BLSKeyPair slashedValidatorKeyPair = blsKeyPairs.get(slashedValidatorIndex);

    postSlashing(
        UInt64.valueOf(3),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        beaconNode,
        slashingEventType);

    validatorClient.waitForLogMessageContaining(
        String.format(
            "Validator(s) with public key(s) %s got slashed. Shutting down...",
            slashedValidatorKeyPair.getPublicKey().toHexString()));
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
                configureNode(config, genesisTime)
                    .withRealNetwork()
                    .withAltairEpoch(altairEpoch)
                    .withInteropValidators(0, 32));

    firstTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(1);

    final TekuNode secondTekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime)
                    .withAltairEpoch(altairEpoch)
                    .withStopVcWhenValidatorSlashedEnabled()
                    .withInteropValidators(32, 32)
                    .withPeers(firstTekuNode));

    secondTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 34;
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 34 + 1);
    final BLSKeyPair slashedValidatorKeyPair = blsKeyPairs.get(slashedValidatorIndex);

    postSlashing(
        UInt64.valueOf(3),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        firstTekuNode,
        slashingEventType);

    secondTekuNode.waitForLogMessageContaining(
        String.format(
            "Validator(s) with public key(s) %s got slashed. Shutting down...",
            slashedValidatorKeyPair.getPublicKey().toHexString()));

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
                configureNode(config, genesisTime)
                    .withRealNetwork()
                    .withAltairEpoch(altairEpoch)
                    .withInteropValidators(0, 32));

    firstTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(1);

    final TekuNode secondBeaconNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime)
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
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 34 + 1);
    final BLSKeyPair slashedValidatorKeyPair = blsKeyPairs.get(slashedValidatorIndex);

    postSlashing(
        UInt64.valueOf(60),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        firstTekuNode,
        slashingEventType);

    secondValidatorClient.waitForLogMessageContaining(
        String.format(
            "Validator(s) with public key(s) %s got slashed. Shutting down...",
            slashedValidatorKeyPair.getPublicKey().toHexString()));

    secondBeaconNode.stop();
    secondValidatorClient.stop();
  }

  private TekuNode.Config configureNode(final TekuNode.Config node, final int genesisTime) {
    return node.withNetwork(network).withGenesisTime(genesisTime).withRealNetwork();
  }

  private void postSlashing(
      final UInt64 slashingSlot,
      final UInt64 slashedIndex,
      final BLSSecretKey slashedValidatorSecretKey,
      final TekuNode tekuNode,
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

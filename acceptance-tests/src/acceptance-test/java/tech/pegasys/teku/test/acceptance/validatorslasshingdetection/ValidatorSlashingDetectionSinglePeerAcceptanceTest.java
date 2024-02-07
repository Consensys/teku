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

package tech.pegasys.teku.test.acceptance.validatorslasshingdetection;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class ValidatorSlashingDetectionSinglePeerAcceptanceTest
    extends ValidatorSlashingDetectionAcceptanceTest {

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void shouldShutDownWhenOwnedValidatorSlashed_SingleProcess_SinglePeer(
      final ValidatorSlashingDetectionAcceptanceTest.SlashingEventType slashingEventType)
      throws Exception {

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
    tekuNode.waitForExit(shutdownWaitingSeconds);
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
    validatorClient.waitForExit(shutdownWaitingSeconds);
    // Make sure the BN didn't shut down
    beaconNode.waitForEpochAtOrAbove(4);
    beaconNode.stop();
  }
}

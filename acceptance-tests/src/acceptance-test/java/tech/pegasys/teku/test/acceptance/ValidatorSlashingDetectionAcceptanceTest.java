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

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockHeaderResponse;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class ValidatorSlashingDetectionAcceptanceTest extends AcceptanceTestBase {

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();
  private final String network = "swift";

  @Test
  void shouldShutDownWhenOwnedValidatorSlashed_SingleProcess_SinglePeer() throws Exception {

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

    final GetBlockHeaderResponse blockHeaderResponse = tekuNode.getBlockHeader("3");
    final BeaconBlockHeader beaconBlockHeader =
        blockHeaderResponse.data.header.message.asInternalBeaconBlockHeader();

    final int validatorIndex = beaconBlockHeader.getProposerIndex().intValue();
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorIndex + 1);
    final BLSKeyPair validatorKeyPair = blsKeyPairs.get(validatorIndex);

    tekuNode.postProposerSlashing(
        beaconBlockHeader.getSlot(),
        beaconBlockHeader.getProposerIndex(),
        beaconBlockHeader.getParentRoot(),
        beaconBlockHeader.getStateRoot(),
        beaconBlockHeader.getBodyRoot(),
        validatorKeyPair.getSecretKey());

    tekuNode.waitForLogMessageContaining(
        String.format(
            "Validator(s) with public key(s) %s got slashed. Shutting down...",
            validatorKeyPair.getPublicKey().toHexString()));
  }

  @Test
  void shouldShutDownWhenOwnedValidatorSlashed_StandAloneVC_SinglePeer() throws Exception {

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

    final GetBlockHeaderResponse blockHeaderResponse = beaconNode.getBlockHeader("3");
    final BeaconBlockHeader beaconBlockHeader =
        blockHeaderResponse.data.header.message.asInternalBeaconBlockHeader();

    final int validatorIndex = beaconBlockHeader.getProposerIndex().intValue();
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorIndex + 1);
    final BLSKeyPair validatorKeyPair = blsKeyPairs.get(validatorIndex);

    beaconNode.postProposerSlashing(
        beaconBlockHeader.getSlot(),
        beaconBlockHeader.getProposerIndex(),
        beaconBlockHeader.getParentRoot(),
        beaconBlockHeader.getStateRoot(),
        beaconBlockHeader.getBodyRoot(),
        validatorKeyPair.getSecretKey());

    validatorClient.waitForLogMessageContaining(
        String.format(
            "Validator(s) with public key(s) %s got slashed. Shutting down...",
            validatorKeyPair.getPublicKey().toHexString()));
  }

  @Test
  void shouldShutDownWhenOwnedValidatorSlashed_SingleProcess_MultiplePeers() throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuNode firstTekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime)
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

    final GetBlockHeaderResponse blockHeaderResponse = firstTekuNode.getBlockHeader("3");
    final BeaconBlockHeader beaconBlockHeader =
        blockHeaderResponse.data.header.message.asInternalBeaconBlockHeader();

    final int slashedValidatorIndex = 34;
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 34 + 1);
    final BLSKeyPair validatorKeyPair = blsKeyPairs.get(slashedValidatorIndex);

    firstTekuNode.postProposerSlashing(
        beaconBlockHeader.getSlot(),
        UInt64.valueOf(slashedValidatorIndex),
        beaconBlockHeader.getParentRoot(),
        beaconBlockHeader.getStateRoot(),
        beaconBlockHeader.getBodyRoot(),
        validatorKeyPair.getSecretKey());

    secondTekuNode.waitForLogMessageContaining(
        String.format(
            "Validator(s) with public key(s) %s got slashed. Shutting down...",
            validatorKeyPair.getPublicKey().toHexString()));

    firstTekuNode.stop();
  }

  @Test
  void shouldShutDownWhenOwnedValidatorSlashed_StandAloneVC_MultiplePeers() throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuNode firstTekuNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime)
                    .withAltairEpoch(altairEpoch)
                    .withInteropValidators(0, 32));

    firstTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(1);

    final TekuNode secondBeaconNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime)
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

    final GetBlockHeaderResponse blockHeaderResponse = firstTekuNode.getBlockHeader("3");
    final BeaconBlockHeader beaconBlockHeader =
        blockHeaderResponse.data.header.message.asInternalBeaconBlockHeader();

    final int slashedValidatorIndex = 34;
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 34 + 1);
    final BLSKeyPair validatorKeyPair = blsKeyPairs.get(slashedValidatorIndex);

    firstTekuNode.postProposerSlashing(
        beaconBlockHeader.getSlot(),
        UInt64.valueOf(slashedValidatorIndex),
        beaconBlockHeader.getParentRoot(),
        beaconBlockHeader.getStateRoot(),
        beaconBlockHeader.getBodyRoot(),
        validatorKeyPair.getSecretKey());

    secondValidatorClient.waitForLogMessageContaining(
        String.format(
            "Validator(s) with public key(s) %s got slashed. Shutting down...",
            validatorKeyPair.getPublicKey().toHexString()));

    secondBeaconNode.stop();
    secondValidatorClient.stop();
  }

  private TekuNode.Config configureNode(final TekuNode.Config node, final int genesisTime) {
    return node.withNetwork(network).withGenesisTime(genesisTime).withRealNetwork();
  }
}

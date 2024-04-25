/*
 * Copyright Consensys Software Inc., 2022
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.io.Resources;
import java.net.URL;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.bellatrix.SignedBeaconBlockBellatrix;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeys;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class TransitionedAnchorAcceptanceTest extends AcceptanceTestBase {
  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");
  // SEED is chosen to have empty slots in the end of 3rd epoch.
  // TODO: Good to find one with earlier end epoch empty slots
  private static final byte[] SEED = new byte[] {0x11, 0x03, 0x04};

  @Test
  @SuppressWarnings("DoNotCreateSecureRandomDirectly")
  void shouldMaintainValidatorsInMutableClient() throws Exception {
    final SecureRandom rnd = SecureRandom.getInstance("SHA1PRNG");
    rnd.setSeed(SEED);
    final String networkName = "swift";

    final List<BLSKeyPair> node1Validators =
        IntStream.range(0, 16).mapToObj(__ -> BLSKeyPair.random(rnd)).toList();
    final List<BLSKeyPair> node2Validators =
        IntStream.range(0, 3).mapToObj(__ -> BLSKeyPair.random(rnd)).toList();

    final ValidatorKeystores node1ValidatorKeystores =
        new ValidatorKeystores(
            node1Validators.stream()
                .map(keyPair -> new ValidatorKeys(keyPair, Bytes32.random(rnd), false))
                .toList());
    // will be non-active in the beginning
    final ValidatorKeystores node2ValidatorKeystores =
        new ValidatorKeystores(
            node2Validators.stream()
                .map(keyPair -> new ValidatorKeys(keyPair, Bytes32.random(rnd), false))
                .toList());

    final InitialStateData genesis =
        createGenesisGenerator()
            .network(networkName)
            .withAltairEpoch(UInt64.ZERO)
            .withBellatrixEpoch(UInt64.ZERO)
            .validatorKeys(node1ValidatorKeystores, node2ValidatorKeystores)
            .generate();

    final String node1FeeRecipient = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";
    final TekuBeaconNode beaconNode1 =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withStubExecutionEngine()
                .withJwtSecretFile(JWT_FILE)
                .withNetwork(networkName)
                .withRealNetwork()
                .withInitialState(genesis)
                .withAltairEpoch(UInt64.ZERO)
                .withBellatrixEpoch(UInt64.ZERO)
                .withValidatorProposerDefaultFeeRecipient(node1FeeRecipient)
                .withReadOnlyKeystorePath(node1ValidatorKeystores)
                .build());

    beaconNode1.start();
    beaconNode1.waitForFinalizationAfter(UInt64.valueOf(3));

    final BeaconState finalizedState = beaconNode1.fetchFinalizedState();
    final UInt64 finalizedSlot = finalizedState.getSlot();
    final UInt64 finalizedEpoch = beaconNode1.getSpec().computeEpochAtSlot(finalizedSlot);
    final UInt64 finalizedEpochFirstSlot =
        beaconNode1.getSpec().computeStartSlotAtEpoch(finalizedEpoch);
    assertNotEquals(finalizedSlot, finalizedEpochFirstSlot);
    final UInt64 maxFinalizedSlot =
        beaconNode1.getSpec().computeStartSlotAtEpoch(finalizedEpoch.plus(1));
    System.out.println(
        "State slot: "
            + finalizedState.getSlot()
            + ", maxSlot: "
            + maxFinalizedSlot
            + ", finalized Epoch: "
            + finalizedEpoch);
    assertTrue(finalizedState.getSlot().isLessThan(maxFinalizedSlot));
    final BeaconState transitionedFinalizedState =
        beaconNode1.getSpec().processSlots(finalizedState, maxFinalizedSlot);

    final String node2FeeRecipient = "0xfe3b557E8Fb62B89F4916B721be55ceB828DBd55";
    final TekuBeaconNode beaconNode2 =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withStubExecutionEngine()
                .withJwtSecretFile(JWT_FILE)
                .withNetwork(networkName)
                .withRealNetwork()
                .withInitialState(new InitialStateData(transitionedFinalizedState))
                .withAltairEpoch(UInt64.ZERO)
                .withBellatrixEpoch(UInt64.ZERO)
                .withValidatorProposerDefaultFeeRecipient(node2FeeRecipient)
                .withReadOnlyKeystorePath(node2ValidatorKeystores)
                .withPeers(beaconNode1)
                .build());

    beaconNode2.start();
    beaconNode2.waitUntilInSyncWith(beaconNode1);
    beaconNode2.waitForBlockSatisfying(
        block -> {
          assertThat(block).isInstanceOf(SignedBeaconBlockBellatrix.class);
          final SignedBeaconBlockBellatrix bellatrixBlock = (SignedBeaconBlockBellatrix) block;
          assertEquals(
              Bytes20.fromHexString(node2FeeRecipient),
              bellatrixBlock.getMessage().getBody().executionPayload.feeRecipient);
        });
    beaconNode2.waitForNewFinalization();
  }
}

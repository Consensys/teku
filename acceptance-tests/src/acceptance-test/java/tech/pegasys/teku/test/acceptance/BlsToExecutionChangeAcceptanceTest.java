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

package tech.pegasys.teku.test.acceptance;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.logic.versions.capella.block.BlockProcessorCapella;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.BlsToExecutionChangeCreator;

public class BlsToExecutionChangeAcceptanceTest extends AcceptanceTestBase {

  private static final String NETWORK_NAME = "swift";

  @Test
  void shouldUpdateWithdrawalCredentials() throws Exception {
    final int validatorIndex = 0;
    // Generating the same keypair that is used for interop validator index = 0
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 1);
    final BLSKeyPair validatorKeyPair = blsKeyPairs.get(validatorIndex);
    final Eth1Address executionAddress =
        Eth1Address.fromHexString("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73");
    final UInt64 capellaActivationEpoch = UInt64.ONE;

    TekuNode primaryNode =
        createTekuNode(
            c -> {
              c.withNetwork(NETWORK_NAME);
              c.withStartupTargetPeerCount(0);
              c.withInteropNumberOfValidators(8);
              c.withInteropValidators(0, 8);
              c.withStubExecutionEngine();
              c.withValidatorProposerDefaultFeeRecipient(executionAddress.toHexString());
              c.withAltairEpoch(UInt64.ZERO);
              c.withBellatrixEpoch(UInt64.ZERO);
              c.withCapellaEpoch(capellaActivationEpoch);
            });
    primaryNode.start();
    primaryNode.waitForOwnedValidatorCount(8);
    primaryNode.waitForMilestone(SpecMilestone.CAPELLA);

    final SignedBlsToExecutionChange signedBlsToExecutionChange =
        new BlsToExecutionChangeCreator(
                primaryNode.getSpec(), primaryNode.getGenesisValidatorsRoot())
            .createAndSign(
                UInt64.valueOf(validatorIndex),
                validatorKeyPair.getPublicKey(),
                executionAddress,
                validatorKeyPair.getSecretKey(),
                capellaActivationEpoch);

    primaryNode.submitBlsToExecutionChange(signedBlsToExecutionChange);

    primaryNode.waitForValidatorWithCredentials(
        validatorIndex,
        BlockProcessorCapella.getWithdrawalAddressFromEth1Address(executionAddress));
  }
}

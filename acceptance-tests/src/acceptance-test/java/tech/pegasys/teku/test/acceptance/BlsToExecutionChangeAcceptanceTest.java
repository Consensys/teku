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
import org.junit.jupiter.api.RepeatedTest;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode.Config;

public class BlsToExecutionChangeAcceptanceTest extends AcceptanceTestBase {

  private static final String NETWORK_NAME = "swift";

//  @Test
  @RepeatedTest(10)
  void shouldUpdateWithdrawalCredentials() throws Exception {
    final int validatorIndex = 0;
    // Generating the same keypair that is used for interop validator index = 0
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 1);
    final BLSKeyPair validatorKeyPair = blsKeyPairs.get(validatorIndex);
    final Eth1Address executionAddress =
        Eth1Address.fromHexString("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73");
    final UInt64 capellaActivationEpoch = UInt64.ZERO;

    final TekuNode primaryNode = createPrimaryNode(executionAddress, capellaActivationEpoch);
    primaryNode.start();
    primaryNode.startEventListener(EventType.bls_to_execution_change);

    final TekuNode lateJoiningNode =
        createLateJoiningNode(capellaActivationEpoch, primaryNode, primaryNode.getGenesisTime());
    lateJoiningNode.start();
    lateJoiningNode.waitUntilInSyncWith(primaryNode);
    lateJoiningNode.startEventListener(EventType.bls_to_execution_change);

    lateJoiningNode.submitBlsToExecutionChange(validatorIndex, validatorKeyPair, executionAddress);
    lateJoiningNode.waitForValidatorWithCredentials(validatorIndex, executionAddress);

    lateJoiningNode.waitForBlsToExecutionChangeEventForValidator(0);
    primaryNode.waitForBlsToExecutionChangeEventForValidator(0);
  }

  private TekuNode createPrimaryNode(
      final Eth1Address executionAddress, final UInt64 capellaActivationEpoch) {
    return createTekuNode(
        c -> {
          c.withNetwork(NETWORK_NAME)
              .withRealNetwork()
              .withStartupTargetPeerCount(0)
              .withValidatorProposerDefaultFeeRecipient(executionAddress.toHexString());
          applyMilestoneConfig(c, capellaActivationEpoch);
        });
  }

  private TekuNode createLateJoiningNode(
      final UInt64 capellaActivationEpoch, final TekuNode primaryNode, final UInt64 genesisTime) {
    return createTekuNode(
        c -> {
          c.withGenesisTime(genesisTime.intValue())
              .withNetwork(NETWORK_NAME)
              .withRealNetwork()
              .withPeers(primaryNode)
              .withInteropValidators(0, 0);
          applyMilestoneConfig(c, capellaActivationEpoch);
        });
  }

  private static void applyMilestoneConfig(final Config c, final UInt64 capellaForkEpoch) {
    c.withAltairEpoch(UInt64.ZERO);
    c.withBellatrixEpoch(UInt64.ZERO);
    c.withCapellaEpoch(capellaForkEpoch);
    c.withStubExecutionEngine();
  }
}

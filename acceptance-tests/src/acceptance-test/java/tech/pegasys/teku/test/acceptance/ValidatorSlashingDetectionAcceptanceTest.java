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
import org.junit.jupiter.params.provider.Arguments;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;

public class ValidatorSlashingDetectionAcceptanceTest extends AcceptanceTestBase {

  final SystemTimeProvider timeProvider = new SystemTimeProvider();
  final String network = "swift";
  final String slashingActionLog =
      "Validator(s) with public key(s) %s got slashed. Shutting down...";
  final int shutdownWaitingSeconds = 60;

  enum SlashingEventType {
    PROPOSER_SLASHING,
    ATTESTER_SLASHING;
  }

  static Stream<Arguments> getSlashingEventTypes() {
    return Stream.of(SlashingEventType.values()).map(Arguments::of);
  }

  TekuNode.Config configureNode(
      final TekuNode.Config node, final int genesisTime, final String network) {
    return node.withNetwork(network).withGenesisTime(genesisTime).withRealNetwork();
  }

  BLSKeyPair getBlsKeyPair(final int slashedValidatorIndex) {
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, slashedValidatorIndex + 1);
    return blsKeyPairs.get(slashedValidatorIndex);
  }

  void postSlashing(
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

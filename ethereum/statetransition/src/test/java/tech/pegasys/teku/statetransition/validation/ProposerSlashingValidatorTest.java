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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.REJECT;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.ProposerSlashingValidator.ProposerSlashingInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ProposerSlashingValidatorTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 25);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final Spec mockSpec = mock(Spec.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private RecentChainData recentChainData;
  private BeaconChainUtil beaconChainUtil;
  private ProposerSlashingValidator proposerSlashingValidator;

  @BeforeEach
  void beforeEach() {
    recentChainData = MemoryOnlyRecentChainData.builder().specProvider(spec).build();
    beaconChainUtil = BeaconChainUtil.create(spec, recentChainData, VALIDATOR_KEYS, true);
    proposerSlashingValidator = new ProposerSlashingValidator(mockSpec, recentChainData);
  }

  @Test
  public void shouldAcceptValidProposerSlashing() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
    when(mockSpec.validateProposerSlashing(getBestState(), slashing)).thenReturn(Optional.empty());
    when(mockSpec.verifyProposerSlashingSignature(
            getBestState(), slashing, BLSSignatureVerifier.SIMPLE))
        .thenReturn(true);
    assertValidationResult(slashing, ACCEPT);
  }

  @Test
  public void shouldRejectInvalidProposerSlashing() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
    when(mockSpec.validateProposerSlashing(getBestState(), slashing))
        .thenReturn(Optional.of(ProposerSlashingInvalidReason.PROPOSER_INDICES_DIFFERENT));
    when(mockSpec.verifyProposerSlashingSignature(
            getBestState(), slashing, BLSSignatureVerifier.SIMPLE))
        .thenReturn(true);
    assertValidationResult(slashing, REJECT);
  }

  @Test
  public void shouldRejectProposerSlashingWithInvalidSignature() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
    when(mockSpec.validateProposerSlashing(getBestState(), slashing)).thenReturn(Optional.empty());
    when(mockSpec.verifyProposerSlashingSignature(
            getBestState(), slashing, BLSSignatureVerifier.SIMPLE))
        .thenReturn(false);
    assertValidationResult(slashing, REJECT);
  }

  @Test
  public void shouldIgnoreProposerSlashingForTheSameProposer() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    ProposerSlashing slashing1 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing2 =
        new ProposerSlashing(slashing1.getHeader1(), slashing1.getHeader2());
    when(mockSpec.validateProposerSlashing(eq(getBestState()), any())).thenReturn(Optional.empty());
    when(mockSpec.verifyProposerSlashingSignature(
            eq(getBestState()), any(), eq(BLSSignatureVerifier.SIMPLE)))
        .thenReturn(true);
    assertValidationResult(slashing1, ACCEPT);
    assertValidationResult(slashing2, IGNORE);
  }

  @Test
  public void shouldRejectProposerSlashingForTwoSignedHeadersWithSameMessageButDifferentSignature()
      throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    SignedBeaconBlockHeader header1 = dataStructureUtil.randomSignedBeaconBlockHeader();
    SignedBeaconBlockHeader header2 =
        new SignedBeaconBlockHeader(header1.getMessage(), BLSTestUtil.randomSignature(100));
    assertThat(header2).isNotEqualTo(header1);
    ProposerSlashing slashing = new ProposerSlashing(header1, header2);
    assertThat(spec.validateProposerSlashing(getBestState(), slashing))
        .isEqualTo(Optional.of(ProposerSlashingInvalidReason.SAME_HEADER));
  }

  private void assertValidationResult(
      final ProposerSlashing slashing, final ValidationResultCode expectedResultCode) {
    assertThat(proposerSlashingValidator.validateForGossip(slashing))
        .isCompletedWithValueMatching(result -> result.code() == expectedResultCode);
  }

  private BeaconState getBestState() {
    final SafeFuture<BeaconState> stateFuture = recentChainData.getBestState().orElseThrow();
    assertThat(stateFuture).isCompleted();
    return stateFuture.join();
  }
}

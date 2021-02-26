/*
 * Copyright 2020 ConsenSys AG.
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

import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.core.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.spec.util.operationvalidators.ProposerSlashingStateTransitionValidator;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ProposerSlashingValidatorTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 25);
  private DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final SpecProvider specProvider = mock(SpecProvider.class);
  private RecentChainData recentChainData;
  private BeaconChainUtil beaconChainUtil;
  private ProposerSlashingValidator proposerSlashingValidator;
  private ProposerSlashingStateTransitionValidator stateTransitionValidator;

  @BeforeEach
  void beforeEach() {
    recentChainData = MemoryOnlyRecentChainData.create(new EventBus());
    beaconChainUtil = BeaconChainUtil.create(recentChainData, VALIDATOR_KEYS, true);
    stateTransitionValidator = mock(ProposerSlashingStateTransitionValidator.class);
    proposerSlashingValidator =
        new ProposerSlashingValidator(specProvider, recentChainData, stateTransitionValidator);
  }

  @Test
  public void shouldAcceptValidProposerSlashing() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
    when(stateTransitionValidator.validate(recentChainData.getBestState().orElseThrow(), slashing))
        .thenReturn(Optional.empty());
    when(specProvider.verifyProposerSlashingSignature(
            recentChainData.getBestState().orElseThrow(), slashing, BLSSignatureVerifier.SIMPLE))
        .thenReturn(true);
    assertThat(proposerSlashingValidator.validateFully(slashing).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectInvalidProposerSlashing() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
    when(stateTransitionValidator.validate(recentChainData.getBestState().orElseThrow(), slashing))
        .thenReturn(
            Optional.of(
                ProposerSlashingStateTransitionValidator.ProposerSlashingInvalidReason
                    .PROPOSER_INDICES_DIFFERENT));
    when(specProvider.verifyProposerSlashingSignature(
            recentChainData.getBestState().orElseThrow(), slashing, BLSSignatureVerifier.SIMPLE))
        .thenReturn(true);
    assertThat(proposerSlashingValidator.validateFully(slashing).code()).isEqualTo(REJECT);
  }

  @Test
  public void shouldRejectProposerSlashingWithInvalidSignature() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    ProposerSlashing slashing = dataStructureUtil.randomProposerSlashing();
    when(stateTransitionValidator.validate(recentChainData.getBestState().orElseThrow(), slashing))
        .thenReturn(Optional.empty());
    when(specProvider.verifyProposerSlashingSignature(
            recentChainData.getBestState().orElseThrow(), slashing, BLSSignatureVerifier.SIMPLE))
        .thenReturn(false);
    assertThat(proposerSlashingValidator.validateFully(slashing).code()).isEqualTo(REJECT);
  }

  @Test
  public void shouldIgnoreProposerSlashingForTheSameProposer() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    ProposerSlashing slashing1 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing2 =
        new ProposerSlashing(slashing1.getHeader_1(), slashing1.getHeader_2());
    when(stateTransitionValidator.validate(eq(recentChainData.getBestState().orElseThrow()), any()))
        .thenReturn(Optional.empty());
    when(specProvider.verifyProposerSlashingSignature(
            eq(recentChainData.getBestState().orElseThrow()),
            any(),
            eq(BLSSignatureVerifier.SIMPLE)))
        .thenReturn(true);
    assertThat(proposerSlashingValidator.validateFully(slashing1).code()).isEqualTo(ACCEPT);
    assertThat(proposerSlashingValidator.validateFully(slashing2).code()).isEqualTo(IGNORE);
  }

  @Test
  public void shouldRejectProposerSlashingForTwoSignedHeadersWithSameMessageButDifferentSignature()
      throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    stateTransitionValidator = new ProposerSlashingStateTransitionValidator();
    SignedBeaconBlockHeader header1 = dataStructureUtil.randomSignedBeaconBlockHeader();
    SignedBeaconBlockHeader header2 =
        new SignedBeaconBlockHeader(header1.getMessage(), BLSTestUtil.randomSignature(100));
    assertThat(header2).isNotEqualTo(header1);
    ProposerSlashing slashing = new ProposerSlashing(header1, header2);
    assertThat(
            stateTransitionValidator.validate(
                recentChainData.getBestState().orElseThrow(), slashing))
        .isEqualTo(
            Optional.of(
                ProposerSlashingStateTransitionValidator.ProposerSlashingInvalidReason
                    .SAME_HEADER));
  }
}

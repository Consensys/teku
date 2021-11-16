/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.core.signatures;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DeletableSignerTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(UInt64.ONE);

  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final BLSSignature signature = dataStructureUtil.randomSignature();
  private final SafeFuture<BLSSignature> signatureFuture = SafeFuture.completedFuture(signature);
  private final Signer delegate = mock(Signer.class);

  private final DeletableSigner signer = new DeletableSigner(delegate);

  @Test
  void signBlock_shouldSignWhenActive() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(6);
    when(delegate.signBlock(block, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.signBlock(block, forkInfo)).isCompletedWithValue(signature);
  }

  @Test
  void signBlock_shouldNotSignWhenDisabled() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(6);
    signer.disable();
    assertThatSafeFuture(signer.signBlock(block, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).signBlock(block, forkInfo);
  }

  @Test
  void createRandaoReveal_shouldCreateWhenActive() {
    when(delegate.createRandaoReveal(UInt64.ONE, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.createRandaoReveal(UInt64.ONE, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void createRandaoReveal_shouldNotCreateWhenDisabled() {
    signer.disable();
    assertThatSafeFuture(signer.createRandaoReveal(UInt64.ONE, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).createRandaoReveal(UInt64.ONE, forkInfo);
  }

  @Test
  void signAttestationData_shouldSignWhenActive() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    when(delegate.signAttestationData(attestationData, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.signAttestationData(attestationData, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signAttestationData_shouldNotSignWhenDisabled() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    signer.disable();
    assertThatSafeFuture(signer.signAttestationData(attestationData, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).signAttestationData(attestationData, forkInfo);
  }

  @Test
  void signAggregationSlot_shouldSignWhenActive() {
    when(delegate.signAggregationSlot(UInt64.ONE, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.signAggregationSlot(UInt64.ONE, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signAggregationSlot_shouldNotSignWhenDisabled() {
    signer.disable();
    assertThatSafeFuture(signer.signAggregationSlot(UInt64.ONE, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).signAggregationSlot(UInt64.ONE, forkInfo);
  }

  @Test
  void signAggregateAndProof_shouldSignWhenActive() {
    final AggregateAndProof aggregateAndProof = dataStructureUtil.randomAggregateAndProof();
    when(delegate.signAggregateAndProof(aggregateAndProof, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.signAggregateAndProof(aggregateAndProof, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signAggregateAndProof_shouldNotSignWhenDisabled() {
    final AggregateAndProof aggregateAndProof = dataStructureUtil.randomAggregateAndProof();
    signer.disable();
    assertThatSafeFuture(signer.signAggregateAndProof(aggregateAndProof, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).signAggregateAndProof(aggregateAndProof, forkInfo);
  }

  @Test
  void signVoluntaryExit_shouldSignWhenActive() {
    final VoluntaryExit aggregateAndProof = dataStructureUtil.randomVoluntaryExit();
    when(delegate.signVoluntaryExit(aggregateAndProof, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.signVoluntaryExit(aggregateAndProof, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signVoluntaryExit_shouldNotSignWhenDisabled() {
    final VoluntaryExit aggregateAndProof = dataStructureUtil.randomVoluntaryExit();
    signer.disable();
    assertThatSafeFuture(signer.signVoluntaryExit(aggregateAndProof, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).signVoluntaryExit(aggregateAndProof, forkInfo);
  }

  @Test
  void signSyncCommitteeMessage_shouldSignWhenActive() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    when(delegate.signSyncCommitteeMessage(UInt64.ONE, root, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.signSyncCommitteeMessage(UInt64.ONE, root, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signSyncCommitteeMessage_shouldNotSignWhenDisabled() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    signer.disable();
    assertThatSafeFuture(signer.signSyncCommitteeMessage(UInt64.ONE, root, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).signSyncCommitteeMessage(UInt64.ONE, root, forkInfo);
  }

  @Test
  void signSyncCommitteeSelectionProof_shouldAlwaysSign() {
    final SyncAggregatorSelectionData syncAggregatorSelectionData =
        syncCommitteeUtil.createSyncAggregatorSelectionData(UInt64.ONE, UInt64.valueOf(1));
    when(delegate.signSyncCommitteeSelectionProof(syncAggregatorSelectionData, forkInfo))
        .thenReturn(signatureFuture);

    assertThatSafeFuture(
            signer.signSyncCommitteeSelectionProof(syncAggregatorSelectionData, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signSyncCommitteeSelectionProof_shouldNotSignWhenDisabled() {
    final SyncAggregatorSelectionData syncAggregatorSelectionData =
        syncCommitteeUtil.createSyncAggregatorSelectionData(UInt64.ONE, UInt64.valueOf(1));
    when(delegate.signSyncCommitteeSelectionProof(syncAggregatorSelectionData, forkInfo))
        .thenReturn(signatureFuture);

    assertThatSafeFuture(
            signer.signSyncCommitteeSelectionProof(syncAggregatorSelectionData, forkInfo))
        .isCompletedWithValue(signature);
  }

  // signContributionAndProof
  @Test
  void signContributionAndProof_shouldAlwaysSign() {

    final ContributionAndProof contributionAndProof =
        dataStructureUtil.randomSignedContributionAndProof(6L).getMessage();
    when(delegate.signContributionAndProof(contributionAndProof, forkInfo))
        .thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signContributionAndProof(contributionAndProof, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signContributionAndProof_shouldNotSignWhenDisabled() {
    final ContributionAndProof contributionAndProof =
        dataStructureUtil.randomSignedContributionAndProof(6L).getMessage();
    when(delegate.signContributionAndProof(contributionAndProof, forkInfo))
        .thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signContributionAndProof(contributionAndProof, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void shouldNotMarkDeletedWhileSigning() {
    SafeFuture<BLSSignature> future = new SafeFuture<>();
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(6);
    when(delegate.signBlock(block, forkInfo)).thenReturn(future);

    final SafeFuture<BLSSignature> future2 = signer.signBlock(block, forkInfo);
    assertThatSafeFuture(future2).isNotCompleted();
    final SafeFuture<Void> disabledSigner =
        SafeFuture.of(SafeFuture.runAsync(() -> signer.disable()));

    assertThatSafeFuture(disabledSigner).isNotCompleted();
    future.complete(dataStructureUtil.randomSignature());
    assertThatSafeFuture(future2).isCompleted();
    assertThat(disabledSigner).isCompleted();
  }
}

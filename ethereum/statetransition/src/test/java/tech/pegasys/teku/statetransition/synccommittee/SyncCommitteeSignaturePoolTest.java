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

package tech.pegasys.teku.statetransition.synccommittee;

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.REJECT;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool.OperationAddedSubscriber;

class SyncCommitteeSignaturePoolTest {

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SyncCommitteeSignatureValidator validator =
      mock(SyncCommitteeSignatureValidator.class);

  private final ValidateableSyncCommitteeSignature signature =
      ValidateableSyncCommitteeSignature.fromValidator(
          dataStructureUtil.randomSyncCommitteeSignature());

  @SuppressWarnings("unchecked")
  private final OperationAddedSubscriber<ValidateableSyncCommitteeSignature> subscriber =
      mock(OperationAddedSubscriber.class);

  private final SyncCommitteeSignaturePool pool = new SyncCommitteeSignaturePool(spec, validator);

  @BeforeEach
  void setUp() {
    when(validator.validate(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));
    signature.setSubcommitteeAssignments(SyncSubcommitteeAssignments.NONE);
  }

  @Test
  void shouldNotifySubscriberWhenValidSignatureAdded() {
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(signature)).thenReturn(SafeFuture.completedFuture(ACCEPT));

    assertThat(pool.add(signature)).isCompletedWithValue(ACCEPT);
    verify(subscriber).onOperationAdded(signature, ACCEPT);
  }

  @Test
  void shouldNotNotifySubscriberWhenInvalidSignatureAdded() {
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(signature)).thenReturn(SafeFuture.completedFuture(REJECT));

    assertThat(pool.add(signature)).isCompletedWithValue(REJECT);
    verifyNoInteractions(subscriber);
  }

  @Test
  void shouldNotNotifySubscriberWhenIgnoredSignatureAdded() {
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(signature)).thenReturn(SafeFuture.completedFuture(IGNORE));

    assertThat(pool.add(signature)).isCompletedWithValue(IGNORE);
    verifyNoInteractions(subscriber);
  }

  @Test
  void shouldCreateEmptyContributionWhenNoSignaturesAvailable() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(slot, blockRoot, 0);

    assertThat(contribution).isEmpty();
  }

  @Test
  void shouldCreateContributionFromSingleMatchingSignature() {
    final ValidateableSyncCommitteeSignature signature =
        ValidateableSyncCommitteeSignature.fromValidator(
            dataStructureUtil.randomSyncCommitteeSignature());
    final int subcommitteeIndex = 3;
    signature.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(1, 1)
            .addAssignment(subcommitteeIndex, 3)
            .build());

    addValid(signature);

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(
            signature.getSlot(), signature.getBeaconBlockRoot(), subcommitteeIndex);

    assertThat(contribution).contains(createContributionFrom(subcommitteeIndex, signature));
  }

  @Test
  void shouldCreateContributionAggregatingMultipleMatchingSignatures() {
    final int subcommitteeIndex = 3;
    final ValidateableSyncCommitteeSignature signature1 =
        ValidateableSyncCommitteeSignature.fromValidator(
            dataStructureUtil.randomSyncCommitteeSignature());
    signature1.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(1, 1)
            .addAssignment(subcommitteeIndex, 3)
            .build());
    addValid(signature1);
    final ValidateableSyncCommitteeSignature signature2 =
        ValidateableSyncCommitteeSignature.fromValidator(
            dataStructureUtil.randomSyncCommitteeSignature(
                signature1.getSlot(), signature1.getBeaconBlockRoot()));
    signature2.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(subcommitteeIndex, 2)
            .addAssignment(5, 1)
            .build());
    addValid(signature2);

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(
            signature1.getSlot(), signature1.getBeaconBlockRoot(), subcommitteeIndex);

    assertThat(contribution)
        .contains(createContributionFrom(subcommitteeIndex, signature1, signature2));
  }

  @Test
  void shouldIncludeSignatureInContributionForAllApplicableSubnets() {
    final ValidateableSyncCommitteeSignature signature =
        ValidateableSyncCommitteeSignature.fromValidator(
            dataStructureUtil.randomSyncCommitteeSignature());
    signature.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(1, 1)
            .addAssignment(3, 3)
            .addAssignment(5, 3)
            .build());

    addValid(signature);

    final UInt64 slot = signature.getSlot();
    final Bytes32 blockRoot = signature.getBeaconBlockRoot();

    // One signature but gets included for all three subnets.
    assertThat(pool.createContribution(slot, blockRoot, 1))
        .contains(createContributionFrom(1, signature));
    assertThat(pool.createContribution(slot, blockRoot, 3))
        .contains(createContributionFrom(3, signature));
    assertThat(pool.createContribution(slot, blockRoot, 5))
        .contains(createContributionFrom(5, signature));
  }

  @Test
  void shouldExcludeSignaturesWhereSlotDoesNotMatch() {
    final ValidateableSyncCommitteeSignature signature =
        ValidateableSyncCommitteeSignature.fromValidator(
            dataStructureUtil.randomSyncCommitteeSignature());
    final int subcommitteeIndex = 3;
    signature.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder().addAssignment(subcommitteeIndex, 3).build());

    addValid(signature);

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(UInt64.ZERO, signature.getBeaconBlockRoot(), subcommitteeIndex);
    assertThat(contribution).isEmpty();
  }

  @Test
  void shouldExcludeSignaturesWhereBlockRootDoesNotMatch() {
    final ValidateableSyncCommitteeSignature signature =
        ValidateableSyncCommitteeSignature.fromValidator(
            dataStructureUtil.randomSyncCommitteeSignature());
    final int subcommitteeIndex = 3;
    signature.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder().addAssignment(subcommitteeIndex, 3).build());

    addValid(signature);

    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(signature.getSlot(), blockRoot, subcommitteeIndex);
    assertThat(contribution).isEmpty();
  }

  @Test
  void shouldExcludeSignaturesWhereSubcommitteeIndexDoesNotMatch() {
    final ValidateableSyncCommitteeSignature signature =
        ValidateableSyncCommitteeSignature.fromValidator(
            dataStructureUtil.randomSyncCommitteeSignature());
    final int subcommitteeIndex = 3;
    signature.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder().addAssignment(subcommitteeIndex, 3).build());

    addValid(signature);

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(signature.getSlot(), signature.getBeaconBlockRoot(), 1);
    assertThat(contribution).isEmpty();
  }

  @Test
  void shouldPruneSignaturesFromOlderSlots() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final int subcommitteeIndex = 2;
    final ValidateableSyncCommitteeSignature signature0 =
        createSignatureInSlot(blockRoot, subcommitteeIndex, 0);
    final ValidateableSyncCommitteeSignature signature1 =
        createSignatureInSlot(blockRoot, subcommitteeIndex, 1);
    final ValidateableSyncCommitteeSignature signature2 =
        createSignatureInSlot(blockRoot, subcommitteeIndex, 2);

    addValid(signature0);
    addValid(signature1);
    addValid(signature2);

    pool.onSlot(UInt64.ZERO);
    assertSignaturesPresentForSlots(blockRoot, subcommitteeIndex, 0, 1, 2);

    // Should keep current and previous slot
    pool.onSlot(UInt64.valueOf(2));
    assertSignaturesPresentForSlots(blockRoot, subcommitteeIndex, 1, 2);
    assertSignaturesAbsentForSlots(blockRoot, subcommitteeIndex, 0);

    // Should be able to remove all signatures
    pool.onSlot(UInt64.valueOf(4));
    assertSignaturesAbsentForSlots(blockRoot, subcommitteeIndex, 0, 1, 2);
  }

  private void addValid(final ValidateableSyncCommitteeSignature signature0) {
    assertThat(pool.add(signature0)).isCompletedWithValue(ACCEPT);
  }

  private void assertSignaturesPresentForSlots(
      final Bytes32 blockRoot, final int subcommitteeIndex, final int... slots) {
    IntStream.of(slots)
        .forEach(
            slot ->
                assertThat(
                        pool.createContribution(UInt64.valueOf(slot), blockRoot, subcommitteeIndex)
                            .orElseThrow()
                            .getSignature()
                            .isInfinity())
                    .isFalse());
  }

  private void assertSignaturesAbsentForSlots(
      final Bytes32 blockRoot, final int subcommitteeIndex, final int... slots) {
    IntStream.of(slots)
        .forEach(
            slot ->
                assertThat(
                        pool.createContribution(UInt64.valueOf(slot), blockRoot, subcommitteeIndex))
                    .describedAs("Contribution at slot %s should be empty", slot)
                    .isEmpty());
  }

  private ValidateableSyncCommitteeSignature createSignatureInSlot(
      final Bytes32 blockRoot, final int subcommitteeIndex, final int slot) {
    final ValidateableSyncCommitteeSignature signature =
        ValidateableSyncCommitteeSignature.fromValidator(
            dataStructureUtil.randomSyncCommitteeSignature(UInt64.valueOf(slot), blockRoot));
    signature.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder().addAssignment(subcommitteeIndex, 1).build());
    return signature;
  }

  private SyncCommitteeContribution createContributionFrom(
      final int subnetId, final ValidateableSyncCommitteeSignature... signatures) {
    checkArgument(signatures.length > 0, "Must specify at least one signature");
    final ValidateableSyncCommitteeSignature template = signatures[0];
    final Set<Integer> participantIds = new HashSet<>();
    final List<BLSSignature> blsSignatures = new ArrayList<>();
    for (ValidateableSyncCommitteeSignature signature : signatures) {
      participantIds.addAll(
          signature
              .getSubcommitteeAssignments()
              .orElseThrow()
              .getParticipationBitIndices(subnetId));
      blsSignatures.add(signature.getSignature().getSignature());
    }
    return spec.getSyncCommitteeUtilRequired(template.getSlot())
        .createSyncCommitteeContribution(
            template.getSlot(),
            template.getBeaconBlockRoot(),
            UInt64.valueOf(subnetId),
            participantIds,
            BLS.aggregate(blsSignatures));
  }
}

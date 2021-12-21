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
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

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
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool.OperationAddedSubscriber;

class SyncCommitteeMessagePoolTest {

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SyncCommitteeMessageValidator validator = mock(SyncCommitteeMessageValidator.class);

  private final ValidateableSyncCommitteeMessage message =
      ValidateableSyncCommitteeMessage.fromValidator(
          dataStructureUtil.randomSyncCommitteeMessage());

  @SuppressWarnings("unchecked")
  private final OperationAddedSubscriber<ValidateableSyncCommitteeMessage> subscriber =
      mock(OperationAddedSubscriber.class);

  private final SyncCommitteeMessagePool pool = new SyncCommitteeMessagePool(spec, validator);

  @BeforeEach
  void setUp() {
    when(validator.validate(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));
    message.setSubcommitteeAssignments(SyncSubcommitteeAssignments.NONE);
  }

  @Test
  void shouldNotifySubscriberWhenValidMessageAdded() {
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(message)).thenReturn(SafeFuture.completedFuture(ACCEPT));

    assertThat(pool.add(message)).isCompletedWithValue(ACCEPT);
    verify(subscriber).onOperationAdded(message, ACCEPT);
  }

  @Test
  void shouldNotNotifySubscriberWhenInvalidMessageAdded() {
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(message)).thenReturn(SafeFuture.completedFuture(reject("Bad")));

    assertThat(pool.add(message)).isCompletedWithValue(reject("Bad"));
    verifyNoInteractions(subscriber);
  }

  @Test
  void shouldNotNotifySubscriberWhenIgnoredMessageAdded() {
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(message)).thenReturn(SafeFuture.completedFuture(IGNORE));

    assertThat(pool.add(message)).isCompletedWithValue(IGNORE);
    verifyNoInteractions(subscriber);
  }

  @Test
  void shouldCreateEmptyContributionWhenNoMessagesAvailable() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(slot, blockRoot, 0);

    assertThat(contribution).isEmpty();
  }

  @Test
  void shouldCreateContributionFromSingleMatchingMessage() {
    final ValidateableSyncCommitteeMessage message =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    final int subcommitteeIndex = 3;
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(1, 1)
            .addAssignment(subcommitteeIndex, 3)
            .build());

    addValid(message);

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(message.getSlot(), message.getBeaconBlockRoot(), subcommitteeIndex);

    assertThat(contribution).contains(createContributionFrom(subcommitteeIndex, message));
  }

  @Test
  void shouldCreateContributionAggregatingMultipleMatchingMessages() {
    final int subcommitteeIndex = 3;
    final ValidateableSyncCommitteeMessage message1 =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    message1.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(1, 1)
            .addAssignment(subcommitteeIndex, 3)
            .build());
    addValid(message1);
    final ValidateableSyncCommitteeMessage message2 =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage(
                message1.getSlot(), message1.getBeaconBlockRoot()));
    message2.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(subcommitteeIndex, 2)
            .addAssignment(5, 1)
            .build());
    addValid(message2);

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(
            message1.getSlot(), message1.getBeaconBlockRoot(), subcommitteeIndex);

    assertThat(contribution)
        .contains(createContributionFrom(subcommitteeIndex, message1, message2));
  }

  @Test
  void shouldIncludeMessageInContributionForAllApplicableSubnets() {
    final ValidateableSyncCommitteeMessage message =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(1, 1)
            .addAssignment(3, 3)
            .addAssignment(5, 3)
            .build());

    addValid(message);

    final UInt64 slot = message.getSlot();
    final Bytes32 blockRoot = message.getBeaconBlockRoot();

    // One message but gets included for all three subnets.
    assertThat(pool.createContribution(slot, blockRoot, 1))
        .contains(createContributionFrom(1, message));
    assertThat(pool.createContribution(slot, blockRoot, 3))
        .contains(createContributionFrom(3, message));
    assertThat(pool.createContribution(slot, blockRoot, 5))
        .contains(createContributionFrom(5, message));
  }

  @Test
  void shouldAggregateSignatureMultipleTimesWhenValidatorInSameSubcommitteeMultipleTimes() {
    final ValidateableSyncCommitteeMessage message =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder()
            .addAssignment(1, 1)
            .addAssignment(1, 2)
            .addAssignment(1, 3)
            .build());

    addValid(message);

    final BLSSignature signature = message.getMessage().getSignature();
    final BLSSignature expectedAggregate = BLS.aggregate(List.of(signature, signature, signature));

    final UInt64 slot = message.getSlot();
    final Bytes32 blockRoot = message.getBeaconBlockRoot();

    // One message but gets included for all three subnets.
    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(slot, blockRoot, 1);
    assertThat(contribution).isPresent();
    assertThat(contribution.orElseThrow().getSignature()).isEqualTo(expectedAggregate);
  }

  @Test
  void shouldExcludeMessagesWhereSlotDoesNotMatch() {
    final ValidateableSyncCommitteeMessage message =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    final int subcommitteeIndex = 3;
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder().addAssignment(subcommitteeIndex, 3).build());

    addValid(message);

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(UInt64.ZERO, message.getBeaconBlockRoot(), subcommitteeIndex);
    assertThat(contribution).isEmpty();
  }

  @Test
  void shouldExcludeMessagesWhereBlockRootDoesNotMatch() {
    final ValidateableSyncCommitteeMessage message =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    final int subcommitteeIndex = 3;
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder().addAssignment(subcommitteeIndex, 3).build());

    addValid(message);

    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(message.getSlot(), blockRoot, subcommitteeIndex);
    assertThat(contribution).isEmpty();
  }

  @Test
  void shouldExcludeMessagesWhereSubcommitteeIndexDoesNotMatch() {
    final ValidateableSyncCommitteeMessage message =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());
    final int subcommitteeIndex = 3;
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder().addAssignment(subcommitteeIndex, 3).build());

    addValid(message);

    final Optional<SyncCommitteeContribution> contribution =
        pool.createContribution(message.getSlot(), message.getBeaconBlockRoot(), 1);
    assertThat(contribution).isEmpty();
  }

  @Test
  void shouldPruneMessagesFromOlderSlots() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final int subcommitteeIndex = 2;
    final ValidateableSyncCommitteeMessage message0 =
        createMessageInSlot(blockRoot, subcommitteeIndex, 0);
    final ValidateableSyncCommitteeMessage message1 =
        createMessageInSlot(blockRoot, subcommitteeIndex, 1);
    final ValidateableSyncCommitteeMessage message2 =
        createMessageInSlot(blockRoot, subcommitteeIndex, 2);

    addValid(message0);
    addValid(message1);
    addValid(message2);

    pool.onSlot(UInt64.ZERO);
    assertMessagesPresentForSlots(blockRoot, subcommitteeIndex, 0, 1, 2);

    // Should keep current and previous slot
    pool.onSlot(UInt64.valueOf(2));
    assertMessagesPresentForSlots(blockRoot, subcommitteeIndex, 1, 2);
    assertMessagesAbsentForSlots(blockRoot, subcommitteeIndex, 0);

    // Should be able to remove all messages
    pool.onSlot(UInt64.valueOf(4));
    assertMessagesAbsentForSlots(blockRoot, subcommitteeIndex, 0, 1, 2);
  }

  private void addValid(final ValidateableSyncCommitteeMessage message0) {
    assertThat(pool.add(message0)).isCompletedWithValue(ACCEPT);
  }

  private void assertMessagesPresentForSlots(
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

  private void assertMessagesAbsentForSlots(
      final Bytes32 blockRoot, final int subcommitteeIndex, final int... slots) {
    IntStream.of(slots)
        .forEach(
            slot ->
                assertThat(
                        pool.createContribution(UInt64.valueOf(slot), blockRoot, subcommitteeIndex))
                    .describedAs("Contribution at slot %s should be empty", slot)
                    .isEmpty());
  }

  private ValidateableSyncCommitteeMessage createMessageInSlot(
      final Bytes32 blockRoot, final int subcommitteeIndex, final int slot) {
    final ValidateableSyncCommitteeMessage message =
        ValidateableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage(UInt64.valueOf(slot), blockRoot));
    message.setSubcommitteeAssignments(
        SyncSubcommitteeAssignments.builder().addAssignment(subcommitteeIndex, 1).build());
    return message;
  }

  private SyncCommitteeContribution createContributionFrom(
      final int subnetId, final ValidateableSyncCommitteeMessage... messages) {
    checkArgument(messages.length > 0, "Must specify at least one message");
    final ValidateableSyncCommitteeMessage template = messages[0];
    final Set<Integer> participantIds = new HashSet<>();
    final List<BLSSignature> blsSignatures = new ArrayList<>();
    for (ValidateableSyncCommitteeMessage message : messages) {
      participantIds.addAll(
          message.getSubcommitteeAssignments().orElseThrow().getParticipationBitIndices(subnetId));
      blsSignatures.add(message.getMessage().getSignature());
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

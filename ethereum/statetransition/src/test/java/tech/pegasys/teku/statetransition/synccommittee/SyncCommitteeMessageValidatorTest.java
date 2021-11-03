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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class SyncCommitteeMessageValidatorTest {
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);

  private final Spec spec =
      TestSpecFactory.createAltair(
          SpecConfigLoader.loadConfig(
              "minimal",
              phase0Builder ->
                  phase0Builder.altairBuilder(
                      altairBuilder ->
                          altairBuilder.syncCommitteeSize(16).altairForkEpoch(UInt64.ZERO))));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.create().specProvider(spec).numberOfValidators(17).build();
  private final ChainBuilder chainBuilder = storageSystem.chainBuilder();
  private final RecentChainData recentChainData = storageSystem.recentChainData();

  private final SyncCommitteeMessageValidator validator =
      new SyncCommitteeMessageValidator(
          spec,
          recentChainData,
          new SyncCommitteeStateUtils(spec, recentChainData),
          AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE),
          timeProvider);

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
  }

  @Test
  void shouldAcceptWhenValid() {
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();
    final SyncSubcommitteeAssignments assignments =
        spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
            .getSubcommitteeAssignments(
                chainBuilder.getLatestBlockAndState().getState(),
                chainBuilder.getLatestEpoch(),
                message.getValidatorIndex());
    final int validSubnetId = assignments.getAssignedSubcommittees().iterator().next();
    final ValidateableSyncCommitteeMessage validateableMessage =
        ValidateableSyncCommitteeMessage.fromNetwork(message, validSubnetId);

    assertThat(validator.validate(validateableMessage)).isCompletedWithValue(ACCEPT);
    // Should store the computed subcommittee assignments for the validator.
    assertThat(validateableMessage.getSubcommitteeAssignments()).contains(assignments);
  }

  @Test
  void shouldAcceptWhenValidInSlotLastSlotOfSyncCommitteePeriod() {
    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(UInt64.ZERO);
    final UInt64 period2StartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO);
    final UInt64 period3StartEpoch =
        syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(period2StartEpoch);
    final UInt64 period2StartSlot = spec.computeStartSlotAtEpoch(period2StartEpoch);
    final UInt64 period3StartSlot = spec.computeStartSlotAtEpoch(period3StartEpoch);
    final UInt64 lastSlotOfPeriod = period3StartSlot.minus(1);

    // The first two sync committees are the same so advance the chain into the second period
    // so we can test going into the third period which is actually different
    final SignedBlockAndState chainHead =
        storageSystem.chainUpdater().advanceChainUntil(period2StartSlot);
    storageSystem.chainUpdater().setCurrentSlot(lastSlotOfPeriod);
    storageSystem.chainUpdater().updateBestBlock(chainHead);

    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(lastSlotOfPeriod, chainHead.getRoot());
    final SyncSubcommitteeAssignments assignments =
        syncCommitteeUtil.getSubcommitteeAssignments(
            chainHead.getState(),
            syncCommitteeUtil.getEpochForDutiesAtSlot(lastSlotOfPeriod),
            message.getValidatorIndex());
    final int validSubnetId = assignments.getAssignedSubcommittees().iterator().next();
    final ValidateableSyncCommitteeMessage validateableMessage =
        ValidateableSyncCommitteeMessage.fromNetwork(message, validSubnetId);
    timeProvider.advanceTimeByMillis(
        spec.getSlotStartTime(lastSlotOfPeriod, recentChainData.getGenesisTime())
            .times(1000)
            .longValue());

    assertThat(validator.validate(validateableMessage)).isCompletedWithValue(ACCEPT);
    // Should store the computed subcommittee assignments for the validator.
    assertThat(validateableMessage.getSubcommitteeAssignments()).contains(assignments);
  }

  @Test
  void shouldRejectWhenAltairIsNotActiveAtSlot() {
    final Spec phase0Spec = TestSpecFactory.createMinimalPhase0();
    final SyncCommitteeMessageValidator validator =
        new SyncCommitteeMessageValidator(
            phase0Spec,
            recentChainData,
            new SyncCommitteeStateUtils(phase0Spec, recentChainData),
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE),
            timeProvider);
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenNotForTheCurrentSlot() {
    final SignedBlockAndState latestBlockAndState = chainBuilder.getLatestBlockAndState();
    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(
            latestBlockAndState.getSlot().plus(1), latestBlockAndState.getRoot());

    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldIgnoreDuplicateMessages() {
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldAllowDuplicateMessagesForDistinctSubnets() {
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(fromNetworkSpy(message, 1, Set.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(fromNetworkSpy(message, 2, Set.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldIgnoreDuplicateMessagesForSameSubnet() {
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(fromNetworkSpy(message, 1, Set.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(fromNetworkSpy(message, 1, Set.of(1, 2))))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldIgnoreDuplicateMessagesForLocalValidatorsInMultipleSubnets() {
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(fromValidatorSpy(message, Set.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(fromValidatorSpy(message, Set.of(2, 1))))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldIgnoreDuplicateMessagesForLocalValidatorsWhenReceivedAgainFromAnySubnet() {
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(fromValidatorSpy(message, Set.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(fromNetworkSpy(message, 1, Set.of(2, 1))))
        .isCompletedWithValue(IGNORE);
    assertThat(validator.validate(fromNetworkSpy(message, 2, Set.of(2, 1))))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldAcceptWhenValidButBeaconBlockIsUnknown() {
    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(
            chainBuilder.getLatestSlot(), dataStructureUtil.randomBytes32());
    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldRejectWhenValidatorIsNotInSyncCommittee() {
    final SignedBlockAndState target = chainBuilder.getLatestBlockAndState();
    final BeaconStateAltair state = BeaconStateAltair.required(target.getState());
    final List<SszPublicKey> committeePubkeys =
        state.getCurrentSyncCommittee().getPubkeys().asList();
    // Find a validator key that isn't in the sync committee
    final BLSPublicKey validatorPublicKey =
        chainBuilder.getValidatorKeys().stream()
            .map(BLSKeyPair::getPublicKey)
            .filter(publicKey -> !committeePubkeys.contains(new SszPublicKey(publicKey)))
            .findAny()
            .orElseThrow();

    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(
            target.getSlot(), target.getRoot(), state, validatorPublicKey);

    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenReceivedOnIncorrectSubnet() {
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();
    // 9 is never a valid subnet
    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromNetwork(message, 9)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenValidatorIsUnknown() {
    final SyncCommitteeMessage template = chainBuilder.createValidSyncCommitteeMessage();
    final SyncCommitteeMessage message =
        template
            .getSchema()
            .create(
                template.getSlot(),
                template.getBeaconBlockRoot(),
                // There's only 16 validators
                UInt64.valueOf(25),
                template.getSignature());
    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenSignatureIsInvalid() {
    final SyncCommitteeMessage template = chainBuilder.createValidSyncCommitteeMessage();
    final SyncCommitteeMessage message =
        template
            .getSchema()
            .create(
                template.getSlot(),
                template.getBeaconBlockRoot(),
                template.getValidatorIndex(),
                dataStructureUtil.randomSignature());
    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  private ValidateableSyncCommitteeMessage fromValidatorSpy(
      SyncCommitteeMessage message, final Set<Integer> subcommitteeIds) {
    final ValidateableSyncCommitteeMessage validateableMessage =
        ValidateableSyncCommitteeMessage.fromValidator(message);
    return createSpy(validateableMessage, subcommitteeIds);
  }

  private ValidateableSyncCommitteeMessage fromNetworkSpy(
      SyncCommitteeMessage message,
      final int receivedSubnetId,
      final Set<Integer> subcommitteeIds) {
    final ValidateableSyncCommitteeMessage validateableMessage =
        ValidateableSyncCommitteeMessage.fromNetwork(message, receivedSubnetId);
    return createSpy(validateableMessage, subcommitteeIds);
  }

  private ValidateableSyncCommitteeMessage createSpy(
      ValidateableSyncCommitteeMessage validateableMessage, final Set<Integer> subcommitteeIds) {
    // Create spies
    final ValidateableSyncCommitteeMessage validateableMessageSpy = spy(validateableMessage);
    validateableMessage.calculateAssignments(
        spec, chainBuilder.getLatestBlockAndState().getState());
    SyncSubcommitteeAssignments assignments =
        validateableMessage.getSubcommitteeAssignments().orElseThrow();
    SyncSubcommitteeAssignments assignmentsSpy = spy(assignments);

    // Overwrite some functionality
    doReturn(assignmentsSpy).when(validateableMessageSpy).calculateAssignments(any(), any());
    doReturn(Optional.of(assignmentsSpy)).when(validateableMessageSpy).getSubcommitteeAssignments();
    doReturn(subcommitteeIds).when(assignmentsSpy).getAssignedSubcommittees();

    return validateableMessageSpy;
  }
}

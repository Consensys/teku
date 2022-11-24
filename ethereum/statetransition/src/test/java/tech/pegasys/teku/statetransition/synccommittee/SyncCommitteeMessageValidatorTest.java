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

package tech.pegasys.teku.statetransition.synccommittee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
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
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class SyncCommitteeMessageValidatorTest {
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);

  private Spec spec;
  private DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;
  private RecentChainData recentChainData;

  private SyncCommitteeMessageValidator validator;

  private void setupWithDefaultSpec() {
    setupWithSpec(TestSpecFactory.createMinimalAltair());
  }

  private SignedBlockAndState setupWithSpec(final Spec spec) {
    this.spec = spec;

    dataStructureUtil = new DataStructureUtil(spec);
    storageSystem =
        InMemoryStorageSystemBuilder.create().specProvider(spec).numberOfValidators(17).build();
    chainBuilder = storageSystem.chainBuilder();
    recentChainData = storageSystem.recentChainData();

    validator =
        new SyncCommitteeMessageValidator(
            spec,
            recentChainData,
            new SyncCommitteeStateUtils(spec, recentChainData),
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE),
            timeProvider);

    return storageSystem.chainUpdater().initializeGenesis();
  }

  @Test
  void shouldAcceptWhenValid() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();
    final SyncSubcommitteeAssignments assignments =
        spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
            .getSubcommitteeAssignments(
                chainBuilder.getLatestBlockAndState().getState(),
                chainBuilder.getLatestEpoch(),
                message.getValidatorIndex());
    final int validSubnetId = assignments.getAssignedSubcommittees().iterator().nextInt();
    final ValidateableSyncCommitteeMessage validateableMessage =
        ValidateableSyncCommitteeMessage.fromNetwork(message, validSubnetId);

    assertThat(validator.validate(validateableMessage)).isCompletedWithValue(ACCEPT);
    // Should store the computed subcommittee assignments for the validator.
    assertThat(validateableMessage.getSubcommitteeAssignments()).contains(assignments);
  }

  @Test
  void shouldAcceptWhenValidInSlotLastSlotOfSyncCommitteePeriod() {
    setupWithDefaultSpec();
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
    final int validSubnetId = assignments.getAssignedSubcommittees().iterator().nextInt();
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
    setupWithDefaultSpec();
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
    setupWithDefaultSpec();
    final SignedBlockAndState latestBlockAndState = chainBuilder.getLatestBlockAndState();
    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(
            latestBlockAndState.getSlot().plus(1), latestBlockAndState.getRoot());

    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldIgnoreDuplicateMessages() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldAllowDuplicateMessagesForDistinctSubnets() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(fromNetworkSpy(message, 1, IntSet.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(fromNetworkSpy(message, 2, IntSet.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldIgnoreDuplicateMessagesForSameSubnet() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(fromNetworkSpy(message, 1, IntSet.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(fromNetworkSpy(message, 1, IntSet.of(1, 2))))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldIgnoreDuplicateMessagesForLocalValidatorsInMultipleSubnets() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(fromValidatorSpy(message, IntSet.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(fromValidatorSpy(message, IntSet.of(2, 1))))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldIgnoreDuplicateMessagesForLocalValidatorsWhenReceivedAgainFromAnySubnet() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();

    assertThat(validator.validate(fromValidatorSpy(message, IntSet.of(1, 2))))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(fromNetworkSpy(message, 1, IntSet.of(2, 1))))
        .isCompletedWithValue(IGNORE);
    assertThat(validator.validate(fromNetworkSpy(message, 2, IntSet.of(2, 1))))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldAcceptWhenValidButBeaconBlockIsUnknown() {
    setupWithDefaultSpec();
    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(
            chainBuilder.getLatestSlot(), dataStructureUtil.randomBytes32());
    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromValidator(message)))
        .isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldRejectWhenValidatorIsNotInSyncCommittee() {
    setupWithSpec(
        TestSpecFactory.createAltair(
            SpecConfigLoader.loadConfig(
                "minimal",
                phase0Builder ->
                    phase0Builder.altairBuilder(
                        altairBuilder ->
                            altairBuilder.syncCommitteeSize(16).altairForkEpoch(UInt64.ZERO)))));
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
    setupWithDefaultSpec();
    final SyncCommitteeMessage message = chainBuilder.createValidSyncCommitteeMessage();
    // 9 is never a valid subnet
    assertThat(validator.validate(ValidateableSyncCommitteeMessage.fromNetwork(message, 9)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenValidatorIsUnknown() {
    setupWithDefaultSpec();
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
    setupWithDefaultSpec();
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

  @Test
  void shouldUseCorrectForkForSignatureVerificationWhenHeadStateIsBeforeNewMilestone() {
    final SignedBlockAndState genesis =
        setupWithSpec(
            TestSpecFactory.createMinimalWithAltairAndBellatrixForkEpoch(UInt64.ZERO, UInt64.ONE));
    final UInt64 bellatrixStartSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    storageSystem.chainUpdater().setCurrentSlot(bellatrixStartSlot);
    timeProvider.advanceTimeBySeconds(
        spec.computeTimeAtSlot(genesis.getState(), bellatrixStartSlot)
            .minus(timeProvider.getTimeInSeconds())
            .longValue());

    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(bellatrixStartSlot, genesis.getRoot());
    final SyncSubcommitteeAssignments assignments =
        spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
            .getSubcommitteeAssignments(
                chainBuilder.getLatestBlockAndState().getState(),
                chainBuilder.getLatestEpoch(),
                message.getValidatorIndex());
    final int validSubnetId = assignments.getAssignedSubcommittees().iterator().nextInt();
    final ValidateableSyncCommitteeMessage validateableMessage =
        ValidateableSyncCommitteeMessage.fromNetwork(message, validSubnetId);
    assertThat(validator.validate(validateableMessage)).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldUseCorrectForkForSignatureVerificationWhenSlotIsJustBeforeNewMilestone() {
    final SignedBlockAndState genesis =
        setupWithSpec(
            TestSpecFactory.createMinimalWithAltairAndBellatrixForkEpoch(UInt64.ZERO, UInt64.ONE));
    final UInt64 lastAltairSlot = spec.computeStartSlotAtEpoch(UInt64.ONE).minus(1);
    storageSystem.chainUpdater().setCurrentSlot(lastAltairSlot);
    timeProvider.advanceTimeBySeconds(
        spec.computeTimeAtSlot(genesis.getState(), lastAltairSlot)
            .minus(timeProvider.getTimeInSeconds())
            .longValue());

    final SyncCommitteeMessage message =
        chainBuilder.createSyncCommitteeMessage(lastAltairSlot, genesis.getRoot());
    final SyncSubcommitteeAssignments assignments =
        spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
            .getSubcommitteeAssignments(
                chainBuilder.getLatestBlockAndState().getState(),
                chainBuilder.getLatestEpoch(),
                message.getValidatorIndex());
    final int validSubnetId = assignments.getAssignedSubcommittees().iterator().nextInt();
    final ValidateableSyncCommitteeMessage validateableMessage =
        ValidateableSyncCommitteeMessage.fromNetwork(message, validSubnetId);
    assertThat(validator.validate(validateableMessage)).isCompletedWithValue(ACCEPT);
  }

  private ValidateableSyncCommitteeMessage fromValidatorSpy(
      SyncCommitteeMessage message, final IntSet subcommitteeIds) {
    final ValidateableSyncCommitteeMessage validateableMessage =
        ValidateableSyncCommitteeMessage.fromValidator(message);
    return createSpy(validateableMessage, subcommitteeIds);
  }

  private ValidateableSyncCommitteeMessage fromNetworkSpy(
      SyncCommitteeMessage message, final int receivedSubnetId, final IntSet subcommitteeIds) {
    final ValidateableSyncCommitteeMessage validateableMessage =
        ValidateableSyncCommitteeMessage.fromNetwork(message, receivedSubnetId);
    return createSpy(validateableMessage, subcommitteeIds);
  }

  private ValidateableSyncCommitteeMessage createSpy(
      ValidateableSyncCommitteeMessage validateableMessage, final IntSet subcommitteeIds) {
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

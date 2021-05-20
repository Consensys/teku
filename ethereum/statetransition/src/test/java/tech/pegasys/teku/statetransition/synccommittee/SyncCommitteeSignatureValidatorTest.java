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
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.REJECT;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.TestConfigLoader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class SyncCommitteeSignatureValidatorTest {

  private final Spec spec =
      TestSpecFactory.createAltair(
          TestConfigLoader.loadConfig(
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

  private final SyncCommitteeSignatureValidator validator =
      new SyncCommitteeSignatureValidator(
          spec, recentChainData, new SyncCommitteeStateUtils(spec, recentChainData));

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
  }

  @Test
  void shouldAcceptWhenValid() {
    final SyncCommitteeSignature signature = chainBuilder.createValidSyncCommitteeSignature();
    final SyncSubcommitteeAssignments assignments =
        spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
            .getSubcommitteeAssignments(
                chainBuilder.getLatestBlockAndState().getState(),
                chainBuilder.getLatestEpoch(),
                signature.getValidatorIndex());
    final int validSubnetId = assignments.getAssignedSubcommittees().iterator().next();
    final ValidateableSyncCommitteeSignature validateableSignature =
        ValidateableSyncCommitteeSignature.fromNetwork(signature, validSubnetId);

    assertThat(validator.validate(validateableSignature)).isCompletedWithValue(ACCEPT);
    // Should store the computed subcommittee assignments for the validator.
    assertThat(validateableSignature.getSubcommitteeAssignments()).contains(assignments);
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

    final SyncCommitteeSignature signature =
        chainBuilder.createSyncCommitteeSignature(lastSlotOfPeriod, chainHead.getRoot());
    final SyncSubcommitteeAssignments assignments =
        syncCommitteeUtil.getSubcommitteeAssignments(
            chainHead.getState(),
            syncCommitteeUtil.getEpochForDutiesAtSlot(lastSlotOfPeriod),
            signature.getValidatorIndex());
    final int validSubnetId = assignments.getAssignedSubcommittees().iterator().next();
    final ValidateableSyncCommitteeSignature validateableSignature =
        ValidateableSyncCommitteeSignature.fromNetwork(signature, validSubnetId);

    assertThat(validator.validate(validateableSignature)).isCompletedWithValue(ACCEPT);
    // Should store the computed subcommittee assignments for the validator.
    assertThat(validateableSignature.getSubcommitteeAssignments()).contains(assignments);
  }

  @Test
  void shouldRejectWhenAltairIsNotActiveAtSlot() {
    final Spec phase0Spec = TestSpecFactory.createMinimalPhase0();
    final SyncCommitteeSignatureValidator validator =
        new SyncCommitteeSignatureValidator(
            phase0Spec, recentChainData, new SyncCommitteeStateUtils(phase0Spec, recentChainData));
    final SyncCommitteeSignature signature = chainBuilder.createValidSyncCommitteeSignature();

    assertThat(validator.validate(ValidateableSyncCommitteeSignature.fromValidator(signature)))
        .isCompletedWithValue(REJECT);
  }

  @Test
  void shouldRejectWhenNotForTheCurrentSlot() {
    final SignedBlockAndState latestBlockAndState = chainBuilder.getLatestBlockAndState();
    final SyncCommitteeSignature signature =
        chainBuilder.createSyncCommitteeSignature(
            latestBlockAndState.getSlot().plus(1), latestBlockAndState.getRoot());

    assertThat(validator.validate(ValidateableSyncCommitteeSignature.fromValidator(signature)))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldIgnoreDuplicateSignatures() {
    final SyncCommitteeSignature signature = chainBuilder.createValidSyncCommitteeSignature();

    assertThat(validator.validate(ValidateableSyncCommitteeSignature.fromValidator(signature)))
        .isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(ValidateableSyncCommitteeSignature.fromValidator(signature)))
        .isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldIgnoreWhenBeaconBlockIsNotKnown() {
    final SyncCommitteeSignature signature =
        chainBuilder.createSyncCommitteeSignature(
            chainBuilder.getLatestSlot(), dataStructureUtil.randomBytes32());
    assertThat(validator.validate(ValidateableSyncCommitteeSignature.fromValidator(signature)))
        .isCompletedWithValue(IGNORE);
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

    final SyncCommitteeSignature signature =
        chainBuilder.createSyncCommitteeSignature(
            target.getSlot(), target.getRoot(), state, validatorPublicKey);

    assertThat(validator.validate(ValidateableSyncCommitteeSignature.fromValidator(signature)))
        .isCompletedWithValue(REJECT);
  }

  @Test
  void shouldRejectWhenReceivedOnIncorrectSubnet() {
    final SyncCommitteeSignature signature = chainBuilder.createValidSyncCommitteeSignature();
    // 9 is never a valid subnet
    assertThat(validator.validate(ValidateableSyncCommitteeSignature.fromNetwork(signature, 9)))
        .isCompletedWithValue(REJECT);
  }

  @Test
  void shouldRejectWhenValidatorIsUnknown() {
    final SyncCommitteeSignature template = chainBuilder.createValidSyncCommitteeSignature();
    final SyncCommitteeSignature signature =
        template
            .getSchema()
            .create(
                template.getSlot(),
                template.getBeaconBlockRoot(),
                // There's only 16 validators
                UInt64.valueOf(25),
                template.getSignature());
    assertThat(validator.validate(ValidateableSyncCommitteeSignature.fromValidator(signature)))
        .isCompletedWithValue(REJECT);
  }

  @Test
  void shouldRejectWhenSignatureIsInvalid() {
    final SyncCommitteeSignature template = chainBuilder.createValidSyncCommitteeSignature();
    final SyncCommitteeSignature signature =
        template
            .getSchema()
            .create(
                template.getSlot(),
                template.getBeaconBlockRoot(),
                template.getValidatorIndex(),
                dataStructureUtil.randomSignature());
    assertThat(validator.validate(ValidateableSyncCommitteeSignature.fromValidator(signature)))
        .isCompletedWithValue(REJECT);
  }
}

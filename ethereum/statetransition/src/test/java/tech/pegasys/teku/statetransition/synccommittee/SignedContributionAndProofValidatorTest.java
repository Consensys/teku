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

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class SignedContributionAndProofValidatorTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final SpecConfigAltair config = SpecConfigAltair.required(spec.getGenesisSpecConfig());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.create().specProvider(spec).numberOfValidators(5).build();
  private final ChainBuilder chainBuilder = storageSystem.chainBuilder();

  private final SignedContributionAndProofValidator validator =
      new SignedContributionAndProofValidator(spec, storageSystem.recentChainData());

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
  }

  @Test
  void shouldAcceptWhenValid() {
    final SignedContributionAndProof message =
        chainBuilder.createValidSignedContributionAndProofBuilder().build();
    final SafeFuture<InternalValidationResult> result = validator.validate(message);
    assertThat(result).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldIgnoreWhenContributionIsNotFromTheCurrentSlot() {
    final SignedContributionAndProof message =
        chainBuilder.createValidSignedContributionAndProofBuilder().build();
    storageSystem
        .chainUpdater()
        .setCurrentSlot(message.getMessage().getContribution().getSlot().plus(1));

    final SafeFuture<InternalValidationResult> result = validator.validate(message);
    assertThat(result).isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldIgnoreWhenBeaconBlockRootIsUnknown() {
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .beaconBlockRoot(dataStructureUtil.randomBytes32())
            .build();
    final SafeFuture<InternalValidationResult> result = validator.validate(message);
    assertThat(result).isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldRejectWhenSubcommitteeIndexIsTooLarge() {
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .subcommitteeIndex(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT + 1)
            .build();
    final SafeFuture<InternalValidationResult> result = validator.validate(message);
    assertThat(result).isCompletedWithValue(REJECT);
  }

  @Test
  void shouldIgnoreWhenAlreadySeen() {
    final SignedContributionAndProof message =
        chainBuilder.createValidSignedContributionAndProofBuilder().build();
    assertThat(validator.validate(message)).isCompletedWithValue(ACCEPT);
    assertThat(validator.validate(message)).isCompletedWithValue(IGNORE);
  }

  @Test
  void shouldRejectWhenAggregatorIndexIsUnknown() {
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .aggregatorIndex(UInt64.valueOf(10_000))
            .build();
    assertThat(validator.validate(message)).isCompletedWithValue(REJECT);
  }

  @Test
  void shouldRejectWhenAggregatorIsNotInSyncCommittee() {
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .aggregatorNotInSyncSubcommittee()
            .build();
    assertThat(validator.validate(message)).isCompletedWithValue(REJECT);
  }

  @Test
  void shouldRejectWhenSelectionProofIsInvalid() {
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .selectionProof(dataStructureUtil.randomSignature())
            .build();
    assertThat(validator.validate(message)).isCompletedWithValue(REJECT);
  }

  @Test
  void shouldRejectWhenSignedContributionAndProofSignatureIsInvalid() {
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .signedContributionAndProofSignature(dataStructureUtil.randomSignature())
            .build();
    assertThat(validator.validate(message)).isCompletedWithValue(REJECT);
  }

  @Test
  void shouldRejectAggregateSignatureIsInvalid() {
    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .addParticipantSignature(dataStructureUtil.randomSignature())
            .build();
    assertThat(validator.validate(message)).isCompletedWithValue(REJECT);
  }

  @Test
  void shouldHandleBeaconBlockRootBeingFromBeforeCurrentSyncCommitteePeriod() {
    final Bytes32 blockRoot = chainBuilder.getLatestBlockAndState().getRoot();
    final int slot = config.getEpochsPerSyncCommitteePeriod() * config.getSlotsPerEpoch() + 1;
    storageSystem.chainUpdater().advanceChain(slot);

    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .beaconBlockRoot(blockRoot)
            // So the signatures get updated for the new block root
            .resetParticipantsToOnlyAggregator()
            .build();
    assertThat(validator.validate(message)).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldIgnoreWhenBeaconBlockRootFromBeforePreviousSyncCommitteePeriod() {
    // Would have to process too many empty slots to get a state we can use to validate so ignore
    final Bytes32 blockRoot = chainBuilder.getLatestBlockAndState().getRoot();
    final int slot = 2 * config.getEpochsPerSyncCommitteePeriod() * config.getSlotsPerEpoch() + 1;
    storageSystem.chainUpdater().advanceChain(slot);

    final SignedContributionAndProof message =
        chainBuilder
            .createValidSignedContributionAndProofBuilder()
            .beaconBlockRoot(blockRoot)
            .build();
    assertThat(validator.validate(message)).isCompletedWithValue(IGNORE);
  }
}

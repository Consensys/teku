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

package tech.pegasys.teku.statetransition.attestation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import it.unimi.dsi.fastutil.ints.IntList;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class AttestationManagerIntegrationTest {

  public static final UInt64 COMMITTEE_INDEX = UInt64.ZERO;
  private final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(UInt64.ONE);
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.create()
          .specProvider(spec)
          .numberOfValidators(spec.getSlotsPerEpoch(UInt64.ZERO))
          .build();
  private final RecentChainData recentChainData = storageSystem.recentChainData();

  private final AggregatingAttestationPool attestationPool =
      new AggregatingAttestationPool(spec, new NoOpMetricsSystem());
  private final ForkChoice forkChoice =
      new ForkChoice(
          spec, new InlineEventThread(), recentChainData, mock(ForkChoiceNotifier.class));

  private final PendingPool<ValidateableAttestation> pendingAttestations =
      PendingPool.createForAttestations(spec);
  private final FutureItems<ValidateableAttestation> futureAttestations =
      FutureItems.create(ValidateableAttestation::getEarliestSlotForForkChoiceProcessing);
  private final SignatureVerificationService signatureVerificationService =
      SignatureVerificationService.createSimple();
  private final AttestationValidator attestationValidator =
      new AttestationValidator(spec, recentChainData, signatureVerificationService);
  private final ActiveValidatorChannel activeValidatorChannel = mock(ActiveValidatorChannel.class);

  private final AttestationManager attestationManager =
      new AttestationManager(
          forkChoice,
          pendingAttestations,
          futureAttestations,
          attestationPool,
          attestationValidator,
          new AggregateAttestationValidator(
              spec, recentChainData, attestationValidator, signatureVerificationService),
          signatureVerificationService,
          activeValidatorChannel);

  // Version of forks with same fork version for previous and current
  // Guarantees that's the version used for signing regardless of slot
  private final Fork altairFork = createForkWithVersion(spec.fork(UInt64.ONE).getCurrent_version());
  private final Fork phase0Fork =
      createForkWithVersion(spec.fork(UInt64.ZERO).getCurrent_version());

  @BeforeEach
  public void setup() {
    storageSystem.chainUpdater().initializeGenesis();
    assertThat(attestationManager.start()).isCompleted();
  }

  private Fork createForkWithVersion(final Bytes4 version) {
    return new Fork(version, version, UInt64.ZERO);
  }

  @AfterEach
  public void cleanup() {
    assertThat(attestationManager.stop()).isCompleted();
  }

  @Test
  void shouldAcceptAttestationsBeforeForkWithOriginalForkId() {
    final UInt64 attestationSlot = UInt64.ONE;

    // Fork choice only runs attestations one slot after they're sent.
    storageSystem.chainUpdater().setCurrentSlot(attestationSlot.plus(1));

    final SignedBlockAndState targetBlockAndState =
        storageSystem.chainBuilder().getLatestBlockAndStateAtSlot(attestationSlot);
    final ValidateableAttestation attestation =
        createAttestation(attestationSlot, targetBlockAndState, phase0Fork);

    final SafeFuture<InternalValidationResult> result =
        attestationManager.addAttestation(attestation);
    assertThat(result).isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @Test
  void shouldRejectAttestationsBeforeForkWithNewForkId() {
    final UInt64 attestationSlot = UInt64.ONE;

    // Fork choice only runs attestations one slot after they're sent.
    storageSystem.chainUpdater().setCurrentSlot(attestationSlot.plus(1));

    final SignedBlockAndState targetBlockAndState =
        storageSystem.chainBuilder().getLatestBlockAndStateAtSlot(attestationSlot);
    final ValidateableAttestation attestation =
        createAttestation(attestationSlot, targetBlockAndState, altairFork);

    final SafeFuture<InternalValidationResult> result =
        attestationManager.addAttestation(attestation);
    assertThat(result)
        .isCompletedWithValueMatching(InternalValidationResult::isReject, "is rejected");
  }

  @Test
  void shouldRejectAttestationsAfterForkWithOldForkId() {
    final UInt64 attestationSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);

    // Fork choice only runs attestations one slot after they're sent.
    storageSystem.chainUpdater().setCurrentSlot(attestationSlot.plus(1));

    final SignedBlockAndState targetBlockAndState =
        storageSystem.chainBuilder().getLatestBlockAndStateAtSlot(attestationSlot);
    final ValidateableAttestation attestation =
        createAttestation(attestationSlot, targetBlockAndState, phase0Fork);

    final SafeFuture<InternalValidationResult> result =
        attestationManager.addAttestation(attestation);
    assertThat(result)
        .isCompletedWithValueMatching(InternalValidationResult::isReject, "is rejected");
  }

  @Test
  void shouldAcceptAttestationsAfterForkWithNewForkId_emptySlots() {
    final UInt64 attestationSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);

    // Fork choice only runs attestations one slot after they're sent.
    storageSystem.chainUpdater().setCurrentSlot(attestationSlot.plus(1));

    final SignedBlockAndState targetBlockAndState =
        storageSystem.chainBuilder().getLatestBlockAndStateAtSlot(attestationSlot);
    final ValidateableAttestation attestation =
        createAttestation(attestationSlot, targetBlockAndState, altairFork);

    final SafeFuture<InternalValidationResult> result =
        attestationManager.addAttestation(attestation);
    assertThat(result).isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @Test
  void shouldAcceptAttestationsAfterForkWithNewForkId_filledSlots() {
    final UInt64 attestationSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final SignedBlockAndState chainHead =
        storageSystem.chainUpdater().advanceChainUntil(attestationSlot);
    storageSystem.chainUpdater().updateBestBlock(chainHead);

    // Fork choice only runs attestations one slot after they're sent.
    storageSystem.chainUpdater().setCurrentSlot(attestationSlot.plus(1));

    final SignedBlockAndState targetBlockAndState =
        storageSystem.chainBuilder().getLatestBlockAndStateAtSlot(attestationSlot);
    final ValidateableAttestation attestation =
        createAttestation(attestationSlot, targetBlockAndState, altairFork);

    final SafeFuture<InternalValidationResult> result =
        attestationManager.addAttestation(attestation);
    assertThat(result).isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  private ValidateableAttestation createAttestation(
      final UInt64 attestationSlot,
      final SignedBlockAndState targetBlockAndState,
      final Fork fork) {
    final int validatorCommitteePosition = 0;
    final IntList committee =
        spec.getBeaconCommittee(targetBlockAndState.getState(), attestationSlot, COMMITTEE_INDEX);
    final int validatorId = committee.getInt(validatorCommitteePosition);

    final AttestationData attestationData =
        spec.getGenericAttestationData(
            attestationSlot, targetBlockAndState.getState(), targetBlockAndState, COMMITTEE_INDEX);

    final ForkInfo forkInfo =
        new ForkInfo(fork, targetBlockAndState.getState().getGenesis_validators_root());
    final BLSSignature signature =
        storageSystem
            .chainBuilder()
            .sign(validatorId, signer -> signer.signAttestationData(attestationData, forkInfo));

    final AttestationSchema attestationSchema =
        spec.atSlot(attestationSlot).getSchemaDefinitions().getAttestationSchema();
    SszBitlist aggregationBits =
        attestationSchema
            .getAggregationBitsSchema()
            .ofBits(committee.size(), validatorCommitteePosition);
    final Attestation attestation =
        attestationSchema.create(aggregationBits, attestationData, signature);
    return ValidateableAttestation.fromNetwork(
        spec,
        attestation,
        spec.computeSubnetForAttestation(targetBlockAndState.getState(), attestation));
  }
}

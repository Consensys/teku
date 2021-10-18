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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult.SAVED_FOR_FUTURE;
import static tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult.SUCCESSFUL;
import static tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult.UNKNOWN_BLOCK;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorCache;

class AttestationManagerTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final ForkChoice forkChoice = mock(ForkChoice.class);
  private final PendingPool<ValidateableAttestation> pendingAttestations =
      PendingPool.createForAttestations(spec);
  private final FutureItems<ValidateableAttestation> futureAttestations =
      FutureItems.create(ValidateableAttestation::getEarliestSlotForForkChoiceProcessing);
  private final SignatureVerificationService signatureVerificationService =
      mock(SignatureVerificationService.class);
  private final ActiveValidatorCache activeValidatorCache = mock(ActiveValidatorCache.class);

  private final AttestationManager attestationManager =
      new AttestationManager(
          forkChoice,
          pendingAttestations,
          futureAttestations,
          attestationPool,
          mock(AttestationValidator.class),
          mock(AggregateAttestationValidator.class),
          signatureVerificationService,
          activeValidatorCache);

  @BeforeEach
  public void setup() {
    when(signatureVerificationService.start()).thenReturn(SafeFuture.completedFuture(null));
    when(signatureVerificationService.stop()).thenReturn(SafeFuture.completedFuture(null));
    assertThat(attestationManager.start()).isCompleted();
  }

  @AfterEach
  public void cleanup() {
    assertThat(attestationManager.stop()).isCompleted();
  }

  @Test
  public void shouldProcessAttestationsThatAreReadyImmediately() {
    final ValidateableAttestation attestation =
        ValidateableAttestation.from(spec, dataStructureUtil.randomAttestation());
    when(forkChoice.onAttestation(any())).thenReturn(completedFuture(SUCCESSFUL));
    attestationManager.onAttestation(attestation).reportExceptions();

    verifyAttestationProcessed(attestation);
    verify(attestationPool).add(attestation);
    assertThat(futureAttestations.size()).isEqualTo(0);
    assertThat(pendingAttestations.size()).isEqualTo(0);
  }

  @Test
  public void shouldProcessAggregatesThatAreReadyImmediately() {
    final ValidateableAttestation aggregate =
        ValidateableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof());
    when(forkChoice.onAttestation(any())).thenReturn(completedFuture(SUCCESSFUL));
    attestationManager.onAttestation(aggregate).reportExceptions();

    verifyAttestationProcessed(aggregate);
    verify(attestationPool).add(aggregate);
    assertThat(futureAttestations.size()).isEqualTo(0);
    assertThat(pendingAttestations.size()).isEqualTo(0);
  }

  @Test
  public void shouldAddAttestationsThatHaveNotYetReachedTargetSlotToFutureItemsAndPool() {
    final int futureSlot = 100;
    final UInt64 currentSlot = UInt64.valueOf(futureSlot).minus(1);
    attestationManager.onSlot(currentSlot);

    ValidateableAttestation attestation =
        ValidateableAttestation.from(spec, attestationFromSlot(futureSlot));
    IndexedAttestation randomIndexedAttestation = dataStructureUtil.randomIndexedAttestation();
    when(forkChoice.onAttestation(any())).thenReturn(completedFuture(SAVED_FOR_FUTURE));
    attestationManager.onAttestation(attestation).reportExceptions();

    ArgumentCaptor<ValidateableAttestation> captor =
        ArgumentCaptor.forClass(ValidateableAttestation.class);
    verify(forkChoice).onAttestation(captor.capture());
    final ValidateableAttestation validateableAttestation = captor.getValue();
    attestation.setIndexedAttestation(randomIndexedAttestation);
    verify(attestationPool).add(attestation);
    assertThat(futureAttestations.contains(captor.getValue())).isTrue();
    assertThat(pendingAttestations.size()).isEqualTo(0);

    // Shouldn't try to process the attestation until after it's slot.
    attestationManager.onSlot(UInt64.valueOf(100));
    assertThat(futureAttestations.size()).isEqualTo(1);
    verify(forkChoice, never()).applyIndexedAttestations(any());

    attestationManager.onSlot(UInt64.valueOf(101));
    verify(forkChoice).applyIndexedAttestations(List.of(validateableAttestation));
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
  }

  @Test
  public void shouldDeferProcessingForAttestationsThatAreMissingBlockDependencies() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    final Bytes32 requiredBlockRoot = block.getMessage().hashTreeRoot();
    final ValidateableAttestation attestation =
        ValidateableAttestation.from(spec, attestationFromSlot(1, requiredBlockRoot));
    when(forkChoice.onAttestation(any()))
        .thenReturn(completedFuture(UNKNOWN_BLOCK))
        .thenReturn(completedFuture(SUCCESSFUL));
    attestationManager.onAttestation(attestation).reportExceptions();

    ArgumentCaptor<ValidateableAttestation> captor =
        ArgumentCaptor.forClass(ValidateableAttestation.class);
    verify(forkChoice).onAttestation(captor.capture());
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.contains(captor.getValue())).isTrue();
    assertThat(pendingAttestations.size()).isEqualTo(1);

    // Slots progressing shouldn't cause the attestation to be processed
    attestationManager.onSlot(UInt64.valueOf(100));
    verifyNoMoreInteractions(forkChoice);

    // Importing a different block shouldn't cause the attestation to be processed
    attestationManager.onBlockImported(dataStructureUtil.randomSignedBeaconBlock(2));
    verifyNoMoreInteractions(forkChoice);

    attestationManager.onBlockImported(block);
    verify(forkChoice, times(2)).onAttestation(captor.getValue());
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    verify(attestationPool).add(attestation);
  }

  @Test
  public void shouldNotPublishProcessedAttestationEventWhenAttestationIsInvalid() {
    final ValidateableAttestation attestation =
        ValidateableAttestation.from(spec, dataStructureUtil.randomAttestation());
    when(forkChoice.onAttestation(any()))
        .thenReturn(completedFuture(AttestationProcessingResult.invalid("Didn't like it")));
    attestationManager.onAttestation(attestation).reportExceptions();

    verifyAttestationProcessed(attestation);
    assertThat(pendingAttestations.size()).isZero();
    assertThat(futureAttestations.size()).isZero();
    verifyNoInteractions(attestationPool);
  }

  @Test
  public void shouldNotPublishProcessedAggregationEventWhenAttestationIsInvalid() {
    final ValidateableAttestation aggregateAndProof =
        ValidateableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof());
    when(forkChoice.onAttestation(any()))
        .thenReturn(completedFuture(AttestationProcessingResult.invalid("Don't wanna")));
    attestationManager.onAttestation(aggregateAndProof).reportExceptions();

    verifyAttestationProcessed(aggregateAndProof);
    assertThat(pendingAttestations.size()).isZero();
    assertThat(futureAttestations.size()).isZero();
    verifyNoInteractions(attestationPool);
  }

  private Attestation attestationFromSlot(final long slot) {
    return attestationFromSlot(slot, Bytes32.ZERO);
  }

  private Attestation attestationFromSlot(final long slot, final Bytes32 targetRoot) {
    return new Attestation(
        Attestation.SSZ_SCHEMA.getAggregationBitsSchema().ofBits(1),
        new AttestationData(
            UInt64.valueOf(slot),
            UInt64.ZERO,
            Bytes32.ZERO,
            new Checkpoint(UInt64.ZERO, Bytes32.ZERO),
            new Checkpoint(UInt64.ZERO, targetRoot)),
        BLSSignature.empty());
  }

  private void verifyAttestationProcessed(final ValidateableAttestation attestation) {
    ArgumentCaptor<ValidateableAttestation> captor =
        ArgumentCaptor.forClass(ValidateableAttestation.class);
    verify(forkChoice).onAttestation(captor.capture());
    assertThat(captor.getValue().getAttestation()).isSameAs(attestation.getAttestation());
  }
}

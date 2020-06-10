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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.AttestationProcessingResult.SAVED_FOR_FUTURE;
import static tech.pegasys.teku.datastructures.util.AttestationProcessingResult.SUCCESSFUL;
import static tech.pegasys.teku.datastructures.util.AttestationProcessingResult.UNKNOWN_BLOCK;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;

class AttestationManagerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = new EventBus();

  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final ForkChoiceAttestationProcessor attestationProcessor =
      mock(ForkChoiceAttestationProcessor.class);
  private final PendingPool<ValidateableAttestation> pendingAttestations =
      PendingPool.createForAttestations();
  private final FutureItems<ValidateableAttestation> futureAttestations =
      new FutureItems<>(ValidateableAttestation::getEarliestSlotForForkChoiceProcessing);

  private final AttestationManager attestationManager =
      new AttestationManager(
          eventBus, attestationProcessor, pendingAttestations, futureAttestations, attestationPool);

  @BeforeEach
  public void setup() {
    assertThat(attestationManager.start()).isCompleted();
  }

  @AfterEach
  public void cleanup() {
    assertThat(attestationManager.stop()).isCompleted();
  }

  @Test
  public void shouldProcessAttestationsThatAreReadyImmediately() {
    final ValidateableAttestation attestation =
        ValidateableAttestation.fromAttestation(dataStructureUtil.randomAttestation());
    when(attestationProcessor.processAttestation(any())).thenReturn(SUCCESSFUL);
    attestationManager.onAttestation(attestation);

    verifyAttestationProcessed(attestation);
    verify(attestationPool).add(attestation);
    assertThat(futureAttestations.size()).isEqualTo(0);
    assertThat(pendingAttestations.size()).isEqualTo(0);
  }

  @Test
  public void shouldProcessAggregatesThatAreReadyImmediately() {
    final ValidateableAttestation aggregate =
        ValidateableAttestation.fromSignedAggregate(
            dataStructureUtil.randomSignedAggregateAndProof());
    when(attestationProcessor.processAttestation(any())).thenReturn(SUCCESSFUL);
    attestationManager.onAttestation(aggregate);

    verifyAttestationProcessed(aggregate);
    verify(attestationPool).add(aggregate);
    assertThat(futureAttestations.size()).isEqualTo(0);
    assertThat(pendingAttestations.size()).isEqualTo(0);
  }

  @Test
  public void shouldAddAttestationsThatHaveNotYetReachedTargetSlotToFutureItemsAndPool() {
    ValidateableAttestation attestation =
        ValidateableAttestation.fromAttestation(attestationFromSlot(100));
    IndexedAttestation randomIndexedAttestation = dataStructureUtil.randomIndexedAttestation();
    when(attestationProcessor.processAttestation(any())).thenReturn(SAVED_FOR_FUTURE);
    attestationManager.onAttestation(attestation);

    ArgumentCaptor<ValidateableAttestation> captor =
        ArgumentCaptor.forClass(ValidateableAttestation.class);
    verify(attestationProcessor).processAttestation(captor.capture());
    captor.getValue().setIndexedAttestation(randomIndexedAttestation);
    verify(attestationPool).add(attestation);
    assertThat(futureAttestations.contains(captor.getValue())).isTrue();
    assertThat(pendingAttestations.size()).isEqualTo(0);

    // Shouldn't try to process the attestation until after it's slot.
    attestationManager.onSlot(UnsignedLong.valueOf(100));
    assertThat(futureAttestations.size()).isEqualTo(1);
    verify(attestationProcessor, never()).applyIndexedAttestationToForkChoice(any());

    attestationManager.onSlot(UnsignedLong.valueOf(101));
    verify(attestationProcessor).applyIndexedAttestationToForkChoice(eq(randomIndexedAttestation));
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
  }

  @Test
  public void shouldDeferProcessingForAttestationsThatAreMissingBlockDependencies() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    final Bytes32 requiredBlockRoot = block.getMessage().hash_tree_root();
    final ValidateableAttestation attestation =
        ValidateableAttestation.fromAttestation(attestationFromSlot(1, requiredBlockRoot));
    when(attestationProcessor.processAttestation(any()))
        .thenReturn(UNKNOWN_BLOCK)
        .thenReturn(SUCCESSFUL);
    attestationManager.onAttestation(attestation);

    ArgumentCaptor<ValidateableAttestation> captor =
        ArgumentCaptor.forClass(ValidateableAttestation.class);
    verify(attestationProcessor).processAttestation(captor.capture());
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.contains(captor.getValue())).isTrue();
    assertThat(pendingAttestations.size()).isEqualTo(1);

    // Slots progressing shouldn't cause the attestation to be processed
    attestationManager.onSlot(UnsignedLong.valueOf(100));
    verifyNoMoreInteractions(attestationProcessor);

    // Importing a different block shouldn't cause the attestation to be processed
    eventBus.post(new ImportedBlockEvent(dataStructureUtil.randomSignedBeaconBlock(2)));
    verifyNoMoreInteractions(attestationProcessor);

    eventBus.post(new ImportedBlockEvent(block));
    verify(attestationProcessor, times(2)).processAttestation(captor.getValue());
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    verify(attestationPool).add(attestation);
  }

  @Test
  public void shouldNotPublishProcessedAttestationEventWhenAttestationIsInvalid() {
    final ValidateableAttestation attestation =
        ValidateableAttestation.fromAttestation(dataStructureUtil.randomAttestation());
    when(attestationProcessor.processAttestation(any()))
        .thenReturn(AttestationProcessingResult.invalid("Didn't like it"));
    attestationManager.onAttestation(attestation);

    verifyAttestationProcessed(attestation);
    assertThat(pendingAttestations.size()).isZero();
    assertThat(futureAttestations.size()).isZero();
    verifyNoInteractions(attestationPool);
  }

  @Test
  public void shouldNotPublishProcessedAggregationEventWhenAttestationIsInvalid() {
    final ValidateableAttestation aggregateAndProof =
        ValidateableAttestation.fromSignedAggregate(
            dataStructureUtil.randomSignedAggregateAndProof());
    when(attestationProcessor.processAttestation(any()))
        .thenReturn(AttestationProcessingResult.invalid("Don't wanna"));
    attestationManager.onAttestation(aggregateAndProof);

    verifyAttestationProcessed(aggregateAndProof);
    assertThat(pendingAttestations.size()).isZero();
    assertThat(futureAttestations.size()).isZero();
    verifyNoInteractions(attestationPool);
  }

  @Test
  void shouldRemoveAttestationsFromTheAggregationPoolWhenTheyAreIncludedInABlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final SSZList<Attestation> attestations = block.getMessage().getBody().getAttestations();
    assertThat(attestations.size()).isNotZero();

    eventBus.post(new ImportedBlockEvent(block));

    attestations.forEach(attestation -> verify(attestationPool).remove(attestation));
  }

  private Attestation attestationFromSlot(final long slot) {
    return attestationFromSlot(slot, Bytes32.ZERO);
  }

  private Attestation attestationFromSlot(final long slot, final Bytes32 targetRoot) {
    return new Attestation(
        new Bitlist(1, 1),
        new AttestationData(
            UnsignedLong.valueOf(slot),
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            new Checkpoint(UnsignedLong.ZERO, Bytes32.ZERO),
            new Checkpoint(UnsignedLong.ZERO, targetRoot)),
        BLSSignature.empty());
  }

  private void verifyAttestationProcessed(final ValidateableAttestation attestation) {
    ArgumentCaptor<ValidateableAttestation> captor =
        ArgumentCaptor.forClass(ValidateableAttestation.class);
    verify(attestationProcessor).processAttestation(captor.capture());
    assertThat(captor.getValue().getAttestation()).isSameAs(attestation.getAttestation());
  }
}

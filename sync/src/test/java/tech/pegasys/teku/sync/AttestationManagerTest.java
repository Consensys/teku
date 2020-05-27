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

package tech.pegasys.teku.sync;

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
import static tech.pegasys.teku.core.results.AttestationProcessingResult.SAVED_FOR_FUTURE;
import static tech.pegasys.teku.core.results.AttestationProcessingResult.SUCCESSFUL;
import static tech.pegasys.teku.core.results.AttestationProcessingResult.UNKNOWN_BLOCK;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.results.AttestationProcessingResult;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.DelayableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.ForkChoiceAttestationProcessor;
import tech.pegasys.teku.statetransition.events.block.ImportedBlockEvent;

class AttestationManagerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = new EventBus();

  private AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private ForkChoiceAttestationProcessor attestationProcessor =
      mock(ForkChoiceAttestationProcessor.class);
  private final PendingPool<DelayableAttestation> pendingAttestations =
      PendingPool.createForAttestations();
  private final FutureItems<DelayableAttestation> futureAttestations =
      new FutureItems<>(DelayableAttestation::getEarliestSlotForForkChoiceProcessing);

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
    Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationProcessor.processAttestation(any())).thenReturn(SUCCESSFUL);
    attestationManager.onGossipedAttestation(attestation);

    verifyAttestationProcessed(attestation);
    verify(attestationPool).add(attestation);
    assertThat(futureAttestations.size()).isEqualTo(0);
    assertThat(pendingAttestations.size()).isEqualTo(0);
  }

  @Test
  public void shouldProcessAggregatesThatAreReadyImmediately() {
    final SignedAggregateAndProof aggregate = dataStructureUtil.randomSignedAggregateAndProof();
    when(attestationProcessor.processAttestation(any())).thenReturn(SUCCESSFUL);
    attestationManager.onGossipedAggregateAndProof(aggregate);

    verifyAttestationProcessed(aggregate.getMessage().getAggregate());
    verify(attestationPool).add(aggregate.getMessage().getAggregate());
    assertThat(futureAttestations.size()).isEqualTo(0);
    assertThat(pendingAttestations.size()).isEqualTo(0);
  }

  @Test
  public void shouldAddAttestationsThatHaveNotYetReachedTargetSlotToFutureItemsAndPool() {
    Attestation attestation = attestationFromSlot(100);
    IndexedAttestation randomIndexedAttestation = dataStructureUtil.randomIndexedAttestation();
    when(attestationProcessor.processAttestation(any())).thenReturn(SAVED_FOR_FUTURE);
    attestationManager.onGossipedAttestation(attestation);

    ArgumentCaptor<DelayableAttestation> captor =
        ArgumentCaptor.forClass(DelayableAttestation.class);
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
    final Attestation attestation = attestationFromSlot(1, requiredBlockRoot);
    when(attestationProcessor.processAttestation(any()))
        .thenReturn(UNKNOWN_BLOCK)
        .thenReturn(SUCCESSFUL);
    attestationManager.onGossipedAttestation(attestation);

    ArgumentCaptor<DelayableAttestation> captor =
        ArgumentCaptor.forClass(DelayableAttestation.class);
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
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationProcessor.processAttestation(any()))
        .thenReturn(AttestationProcessingResult.INVALID);
    attestationManager.onGossipedAttestation(attestation);

    verifyAttestationProcessed(attestation);
    assertThat(pendingAttestations.size()).isZero();
    assertThat(futureAttestations.size()).isZero();
    verifyNoInteractions(attestationPool);
  }

  @Test
  public void shouldNotPublishProcessedAggregationEventWhenAttestationIsInvalid() {
    final SignedAggregateAndProof aggregateAndProof =
        dataStructureUtil.randomSignedAggregateAndProof();
    when(attestationProcessor.processAttestation(any()))
        .thenReturn(AttestationProcessingResult.INVALID);
    attestationManager.onGossipedAggregateAndProof(aggregateAndProof);

    verifyAttestationProcessed(aggregateAndProof.getMessage().getAggregate());
    assertThat(pendingAttestations.size()).isZero();
    assertThat(futureAttestations.size()).isZero();
    verifyNoInteractions(attestationPool);
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

  private void verifyAttestationProcessed(final Attestation attestation) {
    ArgumentCaptor<DelayableAttestation> captor =
        ArgumentCaptor.forClass(DelayableAttestation.class);
    verify(attestationProcessor).processAttestation(captor.capture());
    assertThat(captor.getValue().getAttestation()).isSameAs(attestation);
  }
}

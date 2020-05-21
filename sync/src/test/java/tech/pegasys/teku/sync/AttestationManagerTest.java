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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.core.results.AttestationProcessingResult.SAVED_FOR_FUTURE;
import static tech.pegasys.teku.core.results.AttestationProcessingResult.SUCCESSFUL;
import static tech.pegasys.teku.core.results.AttestationProcessingResult.UNKNOWN_BLOCK;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.results.AttestationProcessingResult;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.DelayableAttestation;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.statetransition.attestation.ForkChoiceAttestationProcessor;
import tech.pegasys.teku.statetransition.events.attestation.ProcessedAggregateEvent;
import tech.pegasys.teku.statetransition.events.attestation.ProcessedAttestationEvent;
import tech.pegasys.teku.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.teku.util.EventSink;

class AttestationManagerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = new EventBus();
  private final PendingPool<DelayableAttestation> pendingAttestations =
      PendingPool.createForAttestations();
  private final FutureItems<DelayableAttestation> futureAttestations =
      new FutureItems<>(DelayableAttestation::getEarliestSlotForForkChoiceProcessing);

  private final ForkChoiceAttestationProcessor attestationProcessor =
      mock(ForkChoiceAttestationProcessor.class);
  private final List<ProcessedAttestationEvent> processedAttestationEvents =
      EventSink.capture(eventBus, ProcessedAttestationEvent.class);
  private final List<ProcessedAggregateEvent> processedAggregateEvents =
      EventSink.capture(eventBus, ProcessedAggregateEvent.class);

  private final AttestationManager attestationManager =
      new AttestationManager(
          eventBus, attestationProcessor, pendingAttestations, futureAttestations);

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
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationProcessor.processAttestation(attestation)).thenReturn(SUCCESSFUL);
    eventBus.post(attestation);

    verify(attestationProcessor).processAttestation(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    assertThat(processedAttestationEvents)
        .containsExactly(new ProcessedAttestationEvent(attestation));
    assertThat(processedAggregateEvents).isEmpty();
  }

  @Test
  public void shouldProcessAggregatesThatAreReadyImmediately() {
    final SignedAggregateAndProof aggregateAndProof =
        dataStructureUtil.randomSignedAggregateAndProof();
    final Attestation aggregate = aggregateAndProof.getMessage().getAggregate();
    when(attestationProcessor.processAttestation(aggregate)).thenReturn(SUCCESSFUL);
    eventBus.post(aggregateAndProof);

    verify(attestationProcessor).processAttestation(aggregate);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    assertThat(processedAggregateEvents).containsExactly(new ProcessedAggregateEvent(aggregate));
    assertThat(processedAttestationEvents).isEmpty();
  }

  @Test
  public void shouldDeferProcessingForAttestationsThatHaveNotYetReachedTargetSlot() {
    final Attestation attestation = attestationFromSlot(100);
    when(attestationProcessor.processAttestation(attestation))
        .thenReturn(SAVED_FOR_FUTURE)
        .thenReturn(SUCCESSFUL);

    eventBus.post(attestation);

    verify(attestationProcessor).processAttestation(attestation);
    assertThat(futureAttestations.size()).isEqualTo(1);
    assertThat(pendingAttestations.size()).isZero();
    assertNoProcessedEvents();

    // Shouldn't try to process the attestation until after it's slot.
    attestationManager.onSlot(UnsignedLong.valueOf(100));
    verifyNoMoreInteractions(attestationProcessor);
    assertNoProcessedEvents();

    attestationManager.onSlot(UnsignedLong.valueOf(101));
    verify(attestationProcessor, times(2)).processAttestation(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    assertThat(processedAttestationEvents)
        .containsExactly(new ProcessedAttestationEvent(attestation));
  }

  @Test
  public void shouldDeferProcessingForAttestationsThatAreMissingBlockDependencies() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    final Bytes32 requiredBlockRoot = block.getMessage().hash_tree_root();
    final Attestation attestation = attestationFromSlot(1, requiredBlockRoot);
    when(attestationProcessor.processAttestation(attestation))
        .thenReturn(UNKNOWN_BLOCK)
        .thenReturn(SUCCESSFUL);

    eventBus.post(attestation);

    verify(attestationProcessor).processAttestation(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isEqualTo(1);
    assertThat(pendingAttestations.size()).isEqualTo(1);
    assertNoProcessedEvents();

    // Slots progressing shouldn't cause the attestation to be processed
    attestationManager.onSlot(UnsignedLong.valueOf(100));
    verifyNoMoreInteractions(attestationProcessor);
    assertNoProcessedEvents();

    // Importing a different block shouldn't cause the attestation to be processed
    eventBus.post(new ImportedBlockEvent(dataStructureUtil.randomSignedBeaconBlock(2)));
    verifyNoMoreInteractions(attestationProcessor);
    assertNoProcessedEvents();

    eventBus.post(new ImportedBlockEvent(block));
    verify(attestationProcessor, times(2)).processAttestation(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    assertThat(processedAttestationEvents)
        .containsExactly(new ProcessedAttestationEvent(attestation));
  }

  @Test
  public void shouldNotPublishProcessedAttestationEventWhenAttestationIsInvalid() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationProcessor.processAttestation(attestation))
        .thenReturn(AttestationProcessingResult.INVALID);

    eventBus.post(attestation);

    verify(attestationProcessor).processAttestation(attestation);
    assertThat(pendingAttestations.size()).isZero();
    assertThat(futureAttestations.size()).isZero();
    assertNoProcessedEvents();
  }

  @Test
  public void shouldNotPublishProcessedAggregationEventWhenAttestationIsInvalid() {
    final AggregateAndProof aggregateAndProof = dataStructureUtil.randomAggregateAndProof();
    final Attestation attestation = aggregateAndProof.getAggregate();
    when(attestationProcessor.processAttestation(attestation))
        .thenReturn(AttestationProcessingResult.INVALID);

    eventBus.post(attestation);

    verify(attestationProcessor).processAttestation(attestation);
    assertThat(pendingAttestations.size()).isZero();
    assertThat(futureAttestations.size()).isZero();
    assertNoProcessedEvents();
  }

  @Test
  public void shouldNotPublishProcessedAggregateEventUntilDelayedAggregateIsProcessedSuccessful() {
    final Attestation attestation = attestationFromSlot(100);
    final SignedAggregateAndProof aggregateAndProof =
        new SignedAggregateAndProof(
            new AggregateAndProof(UnsignedLong.ZERO, attestation, BLSSignature.empty()),
            BLSSignature.empty());
    when(attestationProcessor.processAttestation(attestation))
        .thenReturn(SAVED_FOR_FUTURE)
        .thenReturn(SUCCESSFUL);

    eventBus.post(aggregateAndProof);

    verify(attestationProcessor).processAttestation(attestation);
    assertThat(futureAttestations.size()).isEqualTo(1);
    assertThat(pendingAttestations.size()).isZero();
    assertNoProcessedEvents();

    // Shouldn't try to process the attestation until after it's slot.
    attestationManager.onSlot(UnsignedLong.valueOf(100));
    verifyNoMoreInteractions(attestationProcessor);
    assertNoProcessedEvents();

    attestationManager.onSlot(UnsignedLong.valueOf(101));
    verify(attestationProcessor, times(2)).processAttestation(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    assertThat(processedAttestationEvents).isEmpty();
    assertThat(processedAggregateEvents).containsExactly(new ProcessedAggregateEvent(attestation));
  }

  private void assertNoProcessedEvents() {
    assertThat(processedAttestationEvents).isEmpty();
    assertThat(processedAggregateEvents).isEmpty();
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
}

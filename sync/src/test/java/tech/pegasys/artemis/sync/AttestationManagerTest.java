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

package tech.pegasys.artemis.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.statetransition.attestation.AttestationProcessingResult.FAILED_NOT_FROM_PAST;
import static tech.pegasys.artemis.statetransition.attestation.AttestationProcessingResult.FAILED_UNKNOWN_BLOCK;
import static tech.pegasys.artemis.statetransition.attestation.AttestationProcessingResult.SUCCESSFUL;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.statetransition.attestation.ForkChoiceAttestationProcessor;
import tech.pegasys.artemis.statetransition.events.BlockImportedEvent;
import tech.pegasys.artemis.statetransition.events.ProcessedAggregateEvent;
import tech.pegasys.artemis.statetransition.events.ProcessedAttestationEvent;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.bls.BLSSignature;

class AttestationManagerTest {
  private final EventBus eventBus = new EventBus();
  private final PendingPool<Attestation> pendingAttestations =
      PendingPool.createForAttestations(eventBus);
  private final FutureItems<Attestation> futureAttestations =
      new FutureItems<>(Attestation::getEarliestSlotForProcessing);

  private final ForkChoiceAttestationProcessor attestationProcessor =
      mock(ForkChoiceAttestationProcessor.class);
  private final EventCapture events = new EventCapture();

  private final AttestationManager attestationManager =
      new AttestationManager(
          eventBus, attestationProcessor, pendingAttestations, futureAttestations);

  private int seed = 482942;

  @BeforeEach
  public void setup() {
    assertThat(attestationManager.start()).isCompleted();
    eventBus.register(events);
  }

  @AfterEach
  public void cleanup() {
    assertThat(attestationManager.stop()).isCompleted();
  }

  @Test
  public void shouldProcessAttestationsThatAreReadyImmediately() {
    final Attestation attestation = DataStructureUtil.randomAttestation(seed++);
    when(attestationProcessor.processAttestation(attestation)).thenReturn(SUCCESSFUL);
    eventBus.post(attestation);

    verify(attestationProcessor).processAttestation(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    assertThat(events.processedAttestationEvents)
        .containsExactly(new ProcessedAttestationEvent(attestation));
    assertThat(events.processedAggregateEvents).isEmpty();
  }

  @Test
  public void shouldProcessAggregatesThatAreReadyImmediately() {
    final AggregateAndProof aggregateAndProof = DataStructureUtil.randomAggregateAndProof(seed++);
    when(attestationProcessor.processAttestation(aggregateAndProof.getAggregate()))
        .thenReturn(SUCCESSFUL);
    eventBus.post(aggregateAndProof);

    verify(attestationProcessor).processAttestation(aggregateAndProof.getAggregate());
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    assertThat(events.processedAggregateEvents)
        .containsExactly(new ProcessedAggregateEvent(aggregateAndProof.getAggregate()));
    assertThat(events.processedAttestationEvents).isEmpty();
  }

  @Test
  public void shouldDeferProcessingForAttestationsThatHaveNotYetReachedTargetSlot() {
    final Attestation attestation = attestationFromSlot(100);
    when(attestationProcessor.processAttestation(attestation))
        .thenReturn(FAILED_NOT_FROM_PAST)
        .thenReturn(SUCCESSFUL);

    eventBus.post(attestation);

    verify(attestationProcessor).processAttestation(attestation);
    assertThat(futureAttestations.contains(attestation)).isTrue();
    assertThat(pendingAttestations.size()).isZero();

    // Shouldn't try to process the attestation until after it's slot.
    eventBus.post(new SlotEvent(UnsignedLong.valueOf(100)));
    verifyNoMoreInteractions(attestationProcessor);

    eventBus.post(new SlotEvent(UnsignedLong.valueOf(101)));
    verify(attestationProcessor, times(2)).processAttestation(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
  }

  @Test
  public void shouldDeferProcessingForAttestationsThatAreMissingBlockDependencies() {
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(1, seed++);
    final Bytes32 requiredBlockRoot = block.getMessage().hash_tree_root();
    final Attestation attestation = attestationFromSlot(1, requiredBlockRoot);
    when(attestationProcessor.processAttestation(attestation))
        .thenReturn(FAILED_UNKNOWN_BLOCK)
        .thenReturn(SUCCESSFUL);

    eventBus.post(attestation);

    verify(attestationProcessor).processAttestation(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isEqualTo(1);
    assertThat(pendingAttestations.contains(attestation)).isTrue();

    // Slots progressing shouldn't cause the attestation to be processed
    eventBus.post(new SlotEvent(UnsignedLong.valueOf(100)));
    verifyNoMoreInteractions(attestationProcessor);

    // Importing a different block shouldn't cause the attestation to be processed
    eventBus.post(new BlockImportedEvent(DataStructureUtil.randomSignedBeaconBlock(2, seed++)));
    verifyNoMoreInteractions(attestationProcessor);

    eventBus.post(new BlockImportedEvent(block));
    verify(attestationProcessor, times(2)).processAttestation(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
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

  private static class EventCapture {
    private final List<ProcessedAttestationEvent> processedAttestationEvents = new ArrayList<>();
    private final List<ProcessedAggregateEvent> processedAggregateEvents = new ArrayList<>();

    @Subscribe
    public void onAttestationProcessed(final ProcessedAttestationEvent event) {
      processedAttestationEvents.add(event);
    }

    @Subscribe
    public void onAggregateProcessed(final ProcessedAggregateEvent event) {
      processedAggregateEvents.add(event);
    }
  }
}

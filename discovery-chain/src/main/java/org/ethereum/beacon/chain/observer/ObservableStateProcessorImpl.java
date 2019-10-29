/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.chain.observer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.chain.BeaconChainHead;
import org.ethereum.beacon.chain.BeaconTuple;
import org.ethereum.beacon.chain.BeaconTupleDetails;
import org.ethereum.beacon.chain.LMDGhostHeadFunction;
import org.ethereum.beacon.chain.storage.BeaconChainStorage;
import org.ethereum.beacon.chain.storage.BeaconTupleStorage;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.HeadFunction;
import org.ethereum.beacon.consensus.spec.ForkChoice.LatestMessage;
import org.ethereum.beacon.consensus.transition.EmptySlotTransition;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.state.PendingAttestation;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.schedulers.Scheduler;
import org.ethereum.beacon.schedulers.Schedulers;
import org.ethereum.beacon.stream.SimpleProcessor;
import org.ethereum.beacon.util.cache.Cache;
import org.ethereum.beacon.util.cache.LRUCache;
import org.javatuples.Pair;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

public class ObservableStateProcessorImpl implements ObservableStateProcessor {
  private static final Logger logger = LogManager.getLogger(ObservableStateProcessorImpl.class);

  private static final int MAX_TUPLE_CACHE_SIZE = 32;
  public static final int DEFAULT_EMPTY_SLOT_TRANSITIONS_LIMIT = 1024;

  private final int maxEmptySlotTransitions;

  private final BeaconTupleStorage tupleStorage;

  private final HeadFunction headFunction;
  private final BeaconChainSpec spec;
  private final EmptySlotTransition emptySlotTransition;

  private final Publisher<SlotNumber> slotTicker;
  private final Publisher<Attestation> attestationPublisher;
  private final Publisher<BeaconTupleDetails> beaconPublisher;

  private static final int UPDATE_MILLIS = 500;
  private Scheduler regularJobExecutor;
  private Scheduler continuousJobExecutor;
  private Cache<BeaconBlock, BeaconTupleDetails> tupleDetails =
      new LRUCache<>(MAX_TUPLE_CACHE_SIZE);

  private final List<Attestation> attestationBuffer = new ArrayList<>();
  private final Map<Pair<ValidatorIndex, EpochNumber>, Attestation> attestationCache =
      new HashMap<>();
  private final Schedulers schedulers;

  private final SimpleProcessor<BeaconChainHead> headStream;
  private final SimpleProcessor<ObservableBeaconState> observableStateStream;
  private final SimpleProcessor<PendingOperations> pendingOperationsStream;

  public ObservableStateProcessorImpl(
      BeaconChainStorage chainStorage,
      Publisher<SlotNumber> slotTicker,
      Publisher<Attestation> attestationPublisher,
      Publisher<BeaconTupleDetails> beaconPublisher,
      BeaconChainSpec spec,
      EmptySlotTransition emptySlotTransition,
      Schedulers schedulers) {
    this(
        chainStorage,
        slotTicker,
        attestationPublisher,
        beaconPublisher,
        spec,
        emptySlotTransition,
        schedulers,
        DEFAULT_EMPTY_SLOT_TRANSITIONS_LIMIT);
  }

  public ObservableStateProcessorImpl(
      BeaconChainStorage chainStorage,
      Publisher<SlotNumber> slotTicker,
      Publisher<Attestation> attestationPublisher,
      Publisher<BeaconTupleDetails> beaconPublisher,
      BeaconChainSpec spec,
      EmptySlotTransition emptySlotTransition,
      Schedulers schedulers,
      int maxEmptySlotTransitions) {
    this.tupleStorage = chainStorage.getTupleStorage();
    this.spec = spec;
    this.emptySlotTransition = emptySlotTransition;
    this.headFunction = new LMDGhostHeadFunction(chainStorage, spec);
    this.slotTicker = slotTicker;
    this.attestationPublisher = attestationPublisher;
    this.beaconPublisher = beaconPublisher;
    this.schedulers = schedulers;
    this.maxEmptySlotTransitions = maxEmptySlotTransitions;

    headStream = new SimpleProcessor<>(this.schedulers.events(), "ObservableStateProcessor.head");
    observableStateStream =
        new SimpleProcessor<>(this.schedulers.events(), "ObservableStateProcessor.observableState");
    pendingOperationsStream =
        new SimpleProcessor<>(
            this.schedulers.events(), "PendingOperationsProcessor.pendingOperations");
  }

  @Override
  public void start() {
    regularJobExecutor = schedulers.newSingleThreadDaemon("observable-state-processor-regular");
    continuousJobExecutor =
        schedulers.newSingleThreadDaemon("observable-state-processor-continuous");
    Flux.from(slotTicker).subscribe(this::onNewSlot);
    Flux.from(attestationPublisher).subscribe(this::onNewAttestation);
    Flux.from(beaconPublisher).subscribe(this::onNewBlockTuple);
    regularJobExecutor.executeAtFixedRate(
        Duration.ZERO, Duration.ofMillis(UPDATE_MILLIS), this::doHardWork);
  }

  private void runTaskInSeparateThread(Runnable task) {
    continuousJobExecutor.execute(task::run);
  }

  private void onNewSlot(SlotNumber newSlot) {
    EpochNumber currentEpoch = spec.compute_epoch_of_slot(newSlot);
    EpochNumber previousEpoch =
        currentEpoch.greater(EpochNumber.ZERO) ? currentEpoch.decrement() : currentEpoch;
    runTaskInSeparateThread(
        () -> {
          purgeAttestations(previousEpoch);
          newSlot(newSlot);
        });
  }

  private void doHardWork() {
    if (latestState == null) {
      return;
    }
    List<Attestation> attestations = drainAttestations(spec.get_current_epoch(latestState));
    for (Attestation attestation : attestations) {

      List<ValidatorIndex> participants =
          spec.get_attesting_indices(
              latestState, attestation.getData(), attestation.getAggregationBits());

      participants.forEach(index -> addValidatorAttestation(index, attestation));
    }
  }

  private synchronized void addValidatorAttestation(ValidatorIndex index, Attestation attestation) {
    attestationCache.put(
        Pair.with(index, attestation.getData().getTarget().getEpoch()), attestation);
  }

  private synchronized void onNewAttestation(Attestation attestation) {
    attestationBuffer.add(attestation);
  }

  private synchronized List<Attestation> drainAttestations(EpochNumber upToEpochInclusive) {
    List<Attestation> ret = new ArrayList<>();
    Iterator<Attestation> it = attestationBuffer.iterator();
    while (it.hasNext()) {
      Attestation att = it.next();
      if (att.getData().getTarget().getEpoch().lessEqual(upToEpochInclusive)) {
        ret.add(att);
        it.remove();
      }
    }
    return ret;
  }

  private void onNewBlockTuple(BeaconTupleDetails beaconTuple) {
    tupleDetails.get(beaconTuple.getBlock(), (b) -> beaconTuple);
    runTaskInSeparateThread(
        () -> {
          addAttestationsFromState(beaconTuple.getState());
          updateHead(beaconTuple.getState());
        });
  }

  private void addAttestationsFromState(BeaconState beaconState) {
    List<PendingAttestation> pendingAttestations =
        beaconState.getCurrentEpochAttestations().listCopy();
    pendingAttestations.addAll(beaconState.getPreviousEpochAttestations().listCopy());
    for (PendingAttestation pendingAttestation : pendingAttestations) {
      List<ValidatorIndex> participants =
          spec.get_attesting_indices(
              beaconState, pendingAttestation.getData(), pendingAttestation.getAggregationBits());
      EpochNumber targetEpoch = pendingAttestation.getData().getTarget().getEpoch();
      participants.forEach(
          index -> {
            removeValidatorAttestation(index, targetEpoch);
          });
    }
  }

  private synchronized void removeValidatorAttestation(ValidatorIndex index, EpochNumber epoch) {
    attestationCache.remove(Pair.with(index, epoch));
  }

  /** Purges all entries for epochs before {@code targetEpoch} */
  private synchronized void purgeAttestations(EpochNumber targetEpoch) {
    attestationCache
        .entrySet()
        .removeIf(entry -> entry.getValue().getData().getTarget().getEpoch().less(targetEpoch));
  }

  private synchronized Map<ValidatorIndex, List<Attestation>> copyAttestationCache() {
    return attestationCache.entrySet().stream()
        .collect(
            Collectors.groupingBy(
                e -> e.getKey().getValue0(),
                Collectors.mapping(Entry::getValue, Collectors.toList())));
  }

  private BeaconTupleDetails head;
  private BeaconStateEx latestState;

  private void newHead(BeaconTupleDetails head) {
    this.head = head;
    headStream.onNext(new BeaconChainHead(this.head));

    if (latestState == null) {
      latestState = head.getFinalState();
    }

    if (!head.getBlock().getSlot().greater(latestState.getSlot())) {
      updateCurrentObservableState(head, latestState.getSlot());
    }
  }

  private void newSlot(SlotNumber newSlot) {
    if (head.getBlock().getSlot().greater(newSlot)) {
      logger.info("Ignore new slot " + newSlot + " below head block: " + head.getBlock());
      return;
    }
    if (newSlot.greater(head.getBlock().getSlot().plus(maxEmptySlotTransitions))) {
      logger.debug("Ignore new slot " + newSlot + " far above head block: " + head.getBlock());
      return;
    }

    updateCurrentObservableState(head, newSlot);
  }

  private void updateCurrentObservableState(BeaconTupleDetails head, SlotNumber slot) {
    assert slot.greaterEqual(head.getBlock().getSlot());

    if (slot.greater(head.getBlock().getSlot())) {
      BeaconStateEx stateUponASlot;
      if (latestState.getSlot().greater(spec.getConstants().getGenesisSlot())
          && spec.getObjectHasher()
              .getHashTruncateLast(head.getBlock())
              .equals(
                  spec.get_block_root_at_slot(latestState, latestState.getSlot().decrement()))) {

        // latestState is actual with respect to current head
        stateUponASlot = emptySlotTransition.apply(latestState, slot);
      } else {
        // recalculate all empty slots starting from the head
        stateUponASlot = emptySlotTransition.apply(head.getFinalState(), slot);
      }
      latestState = stateUponASlot;
      PendingOperations pendingOperations =
          getPendingOperations(stateUponASlot, copyAttestationCache());
      observableStateStream.onNext(
          new ObservableBeaconState(head.getBlock(), stateUponASlot, pendingOperations));
    } else {
      PendingOperations pendingOperations =
          getPendingOperations(head.getFinalState(), copyAttestationCache());
      if (head.getPostSlotState().isPresent()) {
        latestState = head.getPostSlotState().get();
        observableStateStream.onNext(
            new ObservableBeaconState(
                head.getBlock(), head.getPostSlotState().get(), pendingOperations));
      }
      if (head.getPostBlockState().isPresent()) {
        latestState = head.getPostBlockState().get();
        observableStateStream.onNext(
            new ObservableBeaconState(
                head.getBlock(), head.getPostBlockState().get(), pendingOperations));
      } else {
        latestState = head.getFinalState();
        observableStateStream.onNext(
            new ObservableBeaconState(head.getBlock(), head.getFinalState(), pendingOperations));
      }
    }
  }

  private PendingOperations getPendingOperations(
      BeaconState state, Map<ValidatorIndex, List<Attestation>> attestationMap) {
    List<Attestation> attestations =
        attestationMap.values().stream()
            .flatMap(Collection::stream)
            .filter(
                attestation ->
                    attestation
                        .getData()
                        .getTarget()
                        .getEpoch()
                        .lessEqual(spec.get_current_epoch(state)))
            .filter(attestation -> spec.verify_attestation(state, attestation))
            .sorted(
                Comparator.comparing(attestation -> attestation.getData().getTarget().getEpoch()))
            .collect(Collectors.toList());

    return new PendingOperationsState(attestations);
  }

  private void updateHead(BeaconState state) {
    Map<ValidatorIndex, List<Attestation>> attestationCacheCopy = copyAttestationCache();
    BeaconBlock newHead =
        headFunction.getHead(
            validatorIndex -> {
              List<Attestation> validatorAttestations =
                  attestationCacheCopy.getOrDefault(validatorIndex, Collections.emptyList());

              return validatorAttestations.stream()
                  .max(
                      Comparator.comparing(
                          attestation -> attestation.getData().getTarget().getEpoch()))
                  .flatMap(
                      a ->
                          Optional.of(
                              new LatestMessage(
                                  spec.compute_epoch_of_slot(
                                      spec.get_attestation_data_slot(state, a.getData())),
                                  a.getData().getBeaconBlockRoot())));
            });
    if (this.head != null && this.head.getBlock().equals(newHead)) {
      return; // == old
    }
    BeaconTupleDetails tuple =
        tupleDetails.get(
            newHead,
            (head) -> {
              BeaconTuple newHeadTuple =
                  tupleStorage
                      .get(spec.signing_root(head))
                      .orElseThrow(
                          () -> new IllegalStateException("Beacon tuple not found for new head "));
              return new BeaconTupleDetails(newHeadTuple);
            });
    newHead(tuple);
  }

  @Override
  public Publisher<BeaconChainHead> getHeadStream() {
    return headStream;
  }

  @Override
  public Publisher<ObservableBeaconState> getObservableStateStream() {
    return observableStateStream;
  }

  @Override
  public Publisher<PendingOperations> getPendingOperationsStream() {
    return pendingOperationsStream;
  }
}

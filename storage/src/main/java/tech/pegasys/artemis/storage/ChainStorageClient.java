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

package tech.pegasys.artemis.storage;

import static tech.pegasys.artemis.datastructures.util.AttestationUtil.getAttesterIndexIntoCommittee;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.getAttesterIndicesIntoCommittee;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.isSingleAttester;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.representsNewAttester;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSAggregate;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;

/** This class is the ChainStorage client-side logic */
public class ChainStorageClient implements ChainStorage {
  protected final EventBus eventBus;
  protected final ConcurrentHashMap<Bytes32, Bitlist> processedAttestationsBitlistMap =
      new ConcurrentHashMap<>();
  protected final ConcurrentHashMap<Bytes32, UnsignedLong> processedAttestationsToEpoch =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Bytes32, Attestation> unprocessedAttestationsMap =
      new ConcurrentHashMap<>();
  private final Map<UnsignedLong, Bytes32> slotToCanonicalBlockRoot = new ConcurrentHashMap<>();

  private final int QUEUE_MAX_SIZE =
      Constants.MAX_VALIDATORS_PER_COMMITTEE
          * Constants.MAX_COMMITTEES_PER_SLOT
          * Constants.SLOTS_PER_EPOCH;
  private final Queue<Attestation> unprocessedAttestationsQueue =
      new PriorityBlockingQueue<>(
          QUEUE_MAX_SIZE, Comparator.comparing(a -> a.getData().getTarget().getEpoch()));

  private Store store;
  private Bytes32 bestBlockRoot = Bytes32.ZERO; // block chosen by lmd ghost to build and attest on
  private UnsignedLong bestSlot =
      UnsignedLong.ZERO; // slot of the block chosen by lmd ghost to build and attest on

  // Time
  private UnsignedLong genesisTime;

  public ChainStorageClient(EventBus eventBus) {
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  public void setGenesisTime(UnsignedLong genesisTime) {
    this.genesisTime = genesisTime;
  }

  public UnsignedLong getGenesisTime() {
    return genesisTime;
  }

  @Subscribe
  public void setStore(Store store) {
    this.store = store;
  }

  public Store getStore() {
    return store;
  }

  // PROCESSED ATTESTATION STORAGE:

  /**
   * Add processed block to storage
   *
   * @param attestation
   */
  public void addProcessedAttestation(Attestation attestation) {

    Bytes32 attestationDataHash = attestation.getData().hash_tree_root();
    ChainStorage.get(attestationDataHash, this.processedAttestationsBitlistMap)
        .ifPresentOrElse(
            oldBitlist -> {
              for (int i = 0; i < attestation.getAggregation_bits().getCurrentSize(); i++) {
                if (attestation.getAggregation_bits().getBit(i) == 1) oldBitlist.setBit(i);
              }
            },
            () -> {
              ChainStorage.add(
                  attestationDataHash,
                  attestation.getAggregation_bits().copy(),
                  this.processedAttestationsBitlistMap);
              ChainStorage.add(
                  attestationDataHash,
                  attestation.getData().getTarget().getEpoch(),
                  processedAttestationsToEpoch);
            });
  }

  public void addUnprocessedAttestation(Attestation newAttestation) {
    // Make sure the new attestation only represents a single attester
    if (isSingleAttester(newAttestation)) {

      Bytes32 attestationDataHashTreeRoot = newAttestation.getData().hash_tree_root();
      Optional<Attestation> aggregateAttestation =
          ChainStorage.get(attestationDataHashTreeRoot, unprocessedAttestationsMap);

      if (aggregateAttestation.isPresent()
          && representsNewAttester(aggregateAttestation.get(), newAttestation)) {

        Attestation oldAggregateAttestation = aggregateAttestation.get();

        // Set the bit of the new attester in the aggregate attestation
        oldAggregateAttestation
            .getAggregation_bits()
            .setBit(getAttesterIndexIntoCommittee(newAttestation));

        // Aggregate signatures
        List<BLSSignature> signaturesToAggregate = new ArrayList<>();
        signaturesToAggregate.add(oldAggregateAttestation.getAggregate_signature());
        signaturesToAggregate.add(newAttestation.getAggregate_signature());
        oldAggregateAttestation.setAggregate_signature(
            BLSAggregate.bls_aggregate_signatures(signaturesToAggregate));
      }
      // If the attestation message hasn't been seen before:
      // - add it to the unprocessed attestation queue to put in a block later
      // - add it to the unprocessed attestation map to aggregate further when
      // another attestation with the same message is received
      else if (aggregateAttestation.isEmpty()) {
        ChainStorage.add(newAttestation, unprocessedAttestationsQueue);
        ChainStorage.add(attestationDataHashTreeRoot, newAttestation, unprocessedAttestationsMap);
      }
    }
  }

  // NETWORKING RELATED INFORMATION METHODS:

  /**
   * Update Best Block
   *
   * @param root
   * @param slot
   */
  public void updateBestBlock(Bytes32 root, UnsignedLong slot) {
    this.bestBlockRoot = root;
    this.bestSlot = slot;
    updateCanonicalBlockIndex(root, slot);
  }

  /**
   * Retrives the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public Bytes32 getBestBlockRoot() {
    return this.bestBlockRoot;
  }

  /**
   * Retrives the state of the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public BeaconState getBestBlockRootState() {
    return this.store.getBlockState(this.bestBlockRoot);
  }

  /**
   * Retrieves the slot of the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public UnsignedLong getBestSlot() {
    return this.bestSlot;
  }

  public Optional<BeaconBlock> getBlockBySlot(UnsignedLong slot) {
    if (store == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(slotToCanonicalBlockRoot.get(slot)).map(store::getBlock);
  }

  /**
   * Retrives the most recent finalized block root
   *
   * @return
   */
  public Bytes32 getFinalizedBlockRoot() {
    return this.store.getFinalizedCheckpoint().getRoot();
  }

  /**
   * Retrives the epoch of the most recent finalized block root
   *
   * @return
   */
  public UnsignedLong getFinalizedEpoch() {
    return this.store.getFinalizedCheckpoint().getEpoch();
  }

  // SUBSCRIPTION METHODS:
  // Place items on queue's for processing, and HashMap
  // for network related quick retrieval

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    STDOUT.log(
        Level.INFO,
        "New BeaconBlock with state root:  " + block.getState_root().toHexString() + " detected.",
        ALogger.Color.GREEN);
  }

  @Subscribe
  public void onNewUnprocessedAttestation(Attestation attestation) {
    STDOUT.log(
        Level.INFO,
        "New Attestation with block root:  "
            + attestation.getData().getBeacon_block_root()
            + " detected.",
        ALogger.Color.GREEN);
  }

  // VALIDATOR COORDINATOR METHODS:

  /**
   * Returns the list of attestations that both have slot number less than or equal to slot, and not
   * included in any processed block. Removes the attestations from the slot sorted queue in the
   * process.
   *
   * @param state
   * @param slot
   * @return
   */
  public SSZList<Attestation> getUnprocessedAttestationsUntilSlot(
      BeaconState state, UnsignedLong slot) {
    SSZList<Attestation> attestations =
        new SSZList<>(Attestation.class, Constants.MAX_ATTESTATIONS);
    int numAttestations = 0;
    while (unprocessedAttestationsQueue.peek() != null
        && unprocessedAttestationsQueue.peek().getData().getSlot().compareTo(slot) <= 0
        && numAttestations < Constants.MAX_ATTESTATIONS) {

      Attestation attestation = unprocessedAttestationsQueue.remove();
      Bytes32 attestationHashTreeRoot = attestation.getData().hash_tree_root();
      unprocessedAttestationsMap.remove(attestationHashTreeRoot);
      // Check if attestation has already been processed in successful block
      if (ChainStorage.get(attestationHashTreeRoot, this.processedAttestationsBitlistMap)
          .isPresent()) {
        Bitlist bitlist =
            ChainStorage.get(
                    attestation.getData().hash_tree_root(), this.processedAttestationsBitlistMap)
                .get();
        List<Integer> oldAttesters = getAttesterIndicesIntoCommittee(bitlist);
        List<Integer> newAttesters =
            getAttesterIndicesIntoCommittee(attestation.getAggregation_bits());
        if (oldAttesters.containsAll(newAttesters)) {
          continue;
        }
      }
      attestations.add(attestation);
      numAttestations++;
    }
    return attestations;
  }

  // Memory Cleaning

  public void cleanAttestationsUntilEpoch(UnsignedLong epoch) {
    Iterator<Bytes32> it = processedAttestationsBitlistMap.keySet().iterator();
    while (it.hasNext()) {
      Bytes32 key = it.next();
      UnsignedLong currentEpoch = processedAttestationsToEpoch.get(key);
      if (currentEpoch.compareTo(epoch) < 0) {
        it.remove();
        processedAttestationsToEpoch.remove(key);
      }
    }
  }

  @Subscribe
  public void onNewSlot(SlotEvent slotEvent) {
    if (slotEvent
            .getSlot()
            .mod(UnsignedLong.valueOf(Constants.SLOTS_PER_EPOCH))
            .compareTo(UnsignedLong.ZERO)
        == 0) {
      UnsignedLong epoch = BeaconStateUtil.compute_epoch_at_slot(slotEvent.getSlot());
      if (epoch.compareTo(UnsignedLong.ONE) > 0) {
        cleanAttestationsUntilEpoch(epoch.minus(UnsignedLong.ONE));
      }
    }
  }

  private void updateCanonicalBlockIndex(final Bytes32 bestBlockRoot, final UnsignedLong slot) {

    final List<UnsignedLong> removedIndices = new ArrayList<>();
    final Map<UnsignedLong, Bytes32> updatedIndices = new HashMap<>();

    UnsignedLong currentSlot = slot;
    Bytes32 currentRoot = bestBlockRoot;
    while (slotToCanonicalBlockRoot.get(currentSlot) != currentRoot) {
      updatedIndices.put(currentSlot, currentRoot);
      if (currentSlot.compareTo(UnsignedLong.valueOf(Constants.GENESIS_SLOT)) <= 0) {
        // We've reached the genesis slot, nothing left to index
        break;
      }
      currentSlot = currentSlot.minus(UnsignedLong.ONE);
      currentRoot = store.getBlock(currentRoot).getParent_root();
    }

    // Remove any slots that should no longer be indexed
    currentSlot = slot.plus(UnsignedLong.ONE);
    while (slotToCanonicalBlockRoot.containsKey(currentSlot)) {
      removedIndices.add(currentSlot);
      currentSlot = currentSlot.plus(UnsignedLong.ONE);
    }
    Collections.reverse(removedIndices);

    removedIndices.forEach(slotToCanonicalBlockRoot::remove);
    slotToCanonicalBlockRoot.putAll(updatedIndices);
  }
}

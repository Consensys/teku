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
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_attestation_data_slot;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.isSingleAttester;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.representsNewAttester;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSAggregate;
import tech.pegasys.artemis.util.bls.BLSSignature;

/** This class is the ChainStorage client-side logic */
public class ChainStorageClient implements ChainStorage {
  private static final ALogger STDOUT = new ALogger("stdout");
  static final ALogger LOG = new ALogger(ChainStorageClient.class.getName());
  static final Integer UNPROCESSED_BLOCKS_LENGTH = 100;
  private Store store;
  protected EventBus eventBus;
  protected final PriorityBlockingQueue<BeaconBlock> unprocessedBlocksQueue =
      new PriorityBlockingQueue<>(
          UNPROCESSED_BLOCKS_LENGTH, Comparator.comparing(BeaconBlock::getSlot));
  private final ConcurrentHashMap<Bytes32, BeaconBlock> unprocessedBlockMap =
      new ConcurrentHashMap<>();
  protected final ConcurrentHashMap<Bytes32, Bitlist> processedAttestationsBitlistMap =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Bytes32, Attestation> unprocessedAttestationsMap =
      new ConcurrentHashMap<>();
  private final Queue<Attestation> unprocessedAttestationsQueue = new LinkedBlockingQueue<>();
  private Bytes32 bestBlockRoot = Bytes32.ZERO; // block chosen by lmd ghost to build and attest on
  private UnsignedLong bestSlot =
      UnsignedLong.ZERO; // slot of the block chosen by lmd ghost to build and attest on

  // Memory cleaning references
  private ConcurrentHashMap<UnsignedLong, List<Bytes32>> blockReferences =
      new ConcurrentHashMap<>();
  private ConcurrentHashMap<UnsignedLong, List<Bytes32>> stateReferences =
      new ConcurrentHashMap<>();

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
            () ->
                ChainStorage.add(
                    attestationDataHash,
                    attestation.getAggregation_bits().copy(),
                    this.processedAttestationsBitlistMap));
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
   * Retrives the slot of the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public UnsignedLong getBestSlot() {
    return this.bestSlot;
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

  // UNPROCESSED ITEM METHODS:

  /**
   * Add unprocessed block to storage
   *
   * @param block
   */
  public void addUnprocessedBlock(BeaconBlock block) {
    ChainStorage.add(block, this.unprocessedBlocksQueue);
    Bytes32 blockHash = block.hash_tree_root();
    ChainStorage.add(blockHash, block, this.unprocessedBlockMap);
  }

  /**
   * Retrieves a list of blocks that were put into storage right after receiving over the wire
   *
   * @param startRoot
   * @param max
   * @param skip
   * @return
   */
  public List<Optional<BeaconBlock>> getUnprocessedBlock(Bytes32 startRoot, long max, long skip) {
    List<Optional<BeaconBlock>> result = new ArrayList<>();
    Bytes32 blockRoot = startRoot;
    Optional<BeaconBlock> block = ChainStorage.get(blockRoot, this.unprocessedBlockMap);
    if (block.isPresent()) {
      result.add(block);
    }
    return result;
  }

  /**
   * Retrieves a list of unprocessed attestations that have not been included in blocks
   *
   * @param attestationHash
   * @return
   */
  public Optional<Attestation> getUnprocessedAttestation(Bytes32 attestationHash) {
    Optional<Attestation> result = Optional.empty();
    if (!this.processedAttestationsBitlistMap.containsKey(attestationHash)
        && this.unprocessedAttestationsMap.containsKey(attestationHash)) {
      result = ChainStorage.get(attestationHash, this.unprocessedAttestationsMap);
    }
    return result;
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
    addUnprocessedBlock(block);
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

  // STATE PROCESSOR METHODS:

  /**
   * Returns the list of blocks that both have slot number less than or equal to slot. Removes the
   * blocks from the slot sorted queue in the process.
   *
   * @param slot
   * @return
   */
  public List<BeaconBlock> getUnprocessedBlocksUntilSlot(UnsignedLong slot) {
    List<BeaconBlock> unprocessedBlocks = new ArrayList<>();
    Optional<BeaconBlock> currentBlock;
    while (!(currentBlock = ChainStorage.peek(this.unprocessedBlocksQueue)).equals(Optional.empty())
        && currentBlock.get().getSlot().compareTo(slot) <= 0) {
      unprocessedBlocks.add(ChainStorage.remove(this.unprocessedBlocksQueue).get());
    }
    return unprocessedBlocks;
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
        && get_attestation_data_slot(state, unprocessedAttestationsQueue.peek().getData())
                .compareTo(slot)
            <= 0
        && numAttestations < Constants.MAX_ATTESTATIONS) {
      Attestation attestation = unprocessedAttestationsQueue.remove();
      // Check if attestation has already been processed in successful block
      if (ChainStorage.get(
              attestation.getData().hash_tree_root(), this.processedAttestationsBitlistMap)
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

  /*
  private void cleanMemory(UnsignedLong latestFinalizedEpoch) {
    CompletableFuture.runAsync(
            () -> {
              cleanOldBlocks(latestFinalizedEpoch);
              cleanOldState(latestFinalizedEpoch);
            });
  }

  private void cleanOldBlocks(UnsignedLong latestFinalizedEpoch) {
    blockReferences.keySet().stream()
            .filter(key -> key.compareTo(latestFinalizedEpoch) < 0)
            .forEach(
                    key -> {
                      ChainStorage.get(key, blockReferences)
                              .ifPresent(
                                      list ->
                                              list.forEach(
                                                      blockRoot -> {
                                                        ChainStorage.remove(blockRoot, processedBlockMap);
                                                        ChainStorage.remove(blockRoot, unprocessedBlockMap);
                                                      }));
                      ChainStorage.remove(key, blockReferences);
                    });
  }

  private void cleanOldState(UnsignedLong latestFinalizedEpoch) {
    stateReferences.keySet().stream()
            .filter(key -> key.compareTo(latestFinalizedEpoch) < 0)
            .forEach(
                    key -> {
                      ChainStorage.get(key, stateReferences)
                              .ifPresent(
                                      list ->
                                              list.forEach(
                                                      stateRoot -> {
                                                        ChainStorage.remove(stateRoot, stateMap);
                                                      }));
                      ChainStorage.remove(key, stateReferences);
                    });
  }

  private void addBlockReference(UnsignedLong epoch, Bytes32 blockRoot) {
    if (ChainStorage.get(epoch, blockReferences).isPresent()) {
      ChainStorage.get(epoch, blockReferences).get().add(blockRoot);
    } else {
      List<Bytes32> epochBlockReferences = new ArrayList<>();
      epochBlockReferences.add(blockRoot);
      ChainStorage.add(epoch, epochBlockReferences, blockReferences);
    }
  }

  private void addStateReference(UnsignedLong epoch, Bytes32 stateRoot) {
    if (ChainStorage.get(epoch, stateReferences).isPresent()) {
      ChainStorage.get(epoch, stateReferences).get().add(stateRoot);
    } else {
      List<Bytes32> epochStateReferences = new ArrayList<>();
      epochStateReferences.add(stateRoot);
      ChainStorage.add(epoch, epochStateReferences, stateReferences);
    }
  }
  */
}

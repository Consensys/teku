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

import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_attestation_data_slot;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_attesting_indices;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.slot_to_epoch;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.util.alogger.ALogger;

/** This class is the ChainStorage client-side logic */
public class ChainStorageClient implements ChainStorage {
  private static final ALogger STDOUT = new ALogger("stdout");
  static final ALogger LOG = new ALogger(ChainStorageClient.class.getName());
  static final Integer UNPROCESSED_BLOCKS_LENGTH = 100;
  protected EventBus eventBus;
  protected final ConcurrentHashMap<Integer, Attestation> latestAttestations =
      new ConcurrentHashMap<>();
  protected final PriorityBlockingQueue<BeaconBlock> unprocessedBlocksQueue =
      new PriorityBlockingQueue<>(
          UNPROCESSED_BLOCKS_LENGTH, Comparator.comparing(BeaconBlock::getSlot));
  protected final ConcurrentHashMap<Bytes32, BeaconBlock> processedBlockMap =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Bytes32, BeaconBlock> unprocessedBlockMap =
      new ConcurrentHashMap<>();
  protected final ConcurrentHashMap<Bytes32, BeaconState> stateMap = new ConcurrentHashMap<>();
  protected final ConcurrentHashMap<Bytes32, Attestation> processedAttestationsMap =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Bytes32, Attestation> unprocessedAttestationsMap =
      new ConcurrentHashMap<>();
  private final Queue<Attestation> unprocessedAttestationsQueue = new LinkedBlockingQueue<>();
  private final ConcurrentHashMap<Integer, List<BeaconBlockHeader>> validatorBlockHeaders =
      new ConcurrentHashMap<>();
  private Bytes32 bestBlockRoot; // block chosen by lmd ghost to build and attest on
  private UnsignedLong bestSlot; // slot of the block chosen by lmd ghost to build and attest on
  private Bytes32 finalizedBlockRoot; // most recent finalized block root
  private UnsignedLong finalizedEpoch; // most recent finalized epoch

  // Memory cleaning references
  private ConcurrentHashMap<UnsignedLong, List<Bytes32>> blockReferences =
      new ConcurrentHashMap<>();
  private ConcurrentHashMap<UnsignedLong, List<Bytes32>> stateReferences =
      new ConcurrentHashMap<>();

  public ChainStorageClient() {}

  public ChainStorageClient(EventBus eventBus) {
    this();
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

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

  /**
   * Add processed block to storage
   *
   * @param blockRoot
   * @param block
   */
  public void addProcessedBlock(Bytes32 blockRoot, BeaconBlock block) {
    ChainStorage.add(blockRoot, block, this.processedBlockMap);
    addBlockReference(slot_to_epoch(block.getSlot()), blockRoot);
    // todo: post event to eventbus to notify the server that a new processed block has been added
  }

  /**
   * Add processed block to storage
   *
   * @param attestation
   */
  public void addProcessedAttestation(Attestation attestation) {
    ChainStorage.add(attestation.hash_tree_root(), attestation, this.processedAttestationsMap);
  }

  /**
   * Add calculated state to storage
   *
   * @param stateRoot
   * @param state
   */
  public void addState(Bytes32 stateRoot, BeaconState state) {
    ChainStorage.add(stateRoot, state, this.stateMap);
    addStateReference(slot_to_epoch(state.getSlot()), stateRoot);
  }

  /**
   * Add unprocessed block to storage
   *
   * @param block
   */
  public void addUnprocessedBlock(BeaconBlock block) {
    ChainStorage.add(block, this.unprocessedBlocksQueue);
    Bytes32 blockHash = block.hash_tree_root();
    ChainStorage.add(blockHash, block, this.unprocessedBlockMap);
    addBlockReference(slot_to_epoch(block.getSlot()), blockHash);
  }

  /**
   * Add unprocessed blockHeader to storage
   *
   * @param blockHeader
   */
  // TODO: implement background process to clean the block headers that were put before finalization
  public void addUnprocessedBlockHeader(int validatorIndex, BeaconBlockHeader blockHeader) {
    if (getBeaconBlockHeaders(validatorIndex).isPresent()) {
      getBeaconBlockHeaders(validatorIndex).get().add(blockHeader);
    } else {
      List<BeaconBlockHeader> blockHeaderList = new ArrayList<>();
      blockHeaderList.add(blockHeader);
      ChainStorage.add(validatorIndex, blockHeaderList, this.validatorBlockHeaders);
    }
  }

  /**
   * Add unprocessed attestation to storage
   *
   * @param attestation
   */
  public void addUnprocessedAttestation(Attestation attestation) {
    ChainStorage.add(attestation, this.unprocessedAttestationsQueue);
    ChainStorage.add(attestation.hash_tree_root(), attestation, this.unprocessedAttestationsMap);
  }

  /**
   * Update Finalized Block
   *
   * @param root
   * @param epoch
   */
  public void updateLatestFinalizedBlock(Bytes32 root, UnsignedLong epoch) {
    this.finalizedBlockRoot = root;
    this.finalizedEpoch = epoch;
    cleanMemory(epoch);
  }

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
   * Retrives the block chosen by lmd ghost to build and attest on
   *
   * @return
   */
  public Bytes32 getBestBlockRoot() {
    return this.bestBlockRoot;
  }

  /**
   * Retrives the slot of the block chosen by lmd ghost to build and attest on
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
    return this.finalizedBlockRoot;
  }

  /**
   * Retrives the epoch of the most recent finalized block root
   *
   * @return
   */
  public UnsignedLong getFinalizedEpoch() {
    return this.finalizedEpoch;
  }

  /**
   * Retrieves processed block
   *
   * @param blockRoot
   * @return
   */
  public Optional<BeaconBlock> getProcessedBlock(Bytes32 blockRoot) {
    return ChainStorage.get(blockRoot, this.processedBlockMap);
  }

  /**
   * Retrieves a list of processed blocks
   *
   * @param startRoot
   * @param max
   * @param skip
   * @return
   */
  public List<Optional<BeaconBlock>> getUnprocessedBlock(Bytes32 startRoot, long max, long skip) {
    List<Optional<BeaconBlock>> result = new ArrayList<>();
    Bytes32 blockRoot = startRoot;
    // Optional<BeaconBlock> block = ChainStorage.get(blockRoot, this.processedBlockMap);
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
    if (!this.processedAttestationsMap.containsKey(attestationHash)
        && this.unprocessedAttestationsMap.containsKey(attestationHash)) {
      result = ChainStorage.get(attestationHash, this.unprocessedAttestationsMap);
    }
    return result;
  }

  /**
   * Retrieves processed block's parent block
   *
   * @param block
   * @return
   */
  public Optional<BeaconBlock> getParent(BeaconBlock block) {
    Bytes32 parentRoot = block.getParent_root();
    return this.getProcessedBlock(parentRoot);
  }

  /**
   * Retrieves state
   *
   * @param stateRoot
   * @return
   */
  public Optional<BeaconState> getState(Bytes32 stateRoot) {
    return ChainStorage.get(stateRoot, this.stateMap);
  }

  /**
   * Gets unprocessed blocks (LIFO)
   *
   * @return
   */
  public List<BeaconBlock> getUnprocessedBlocks() {
    return new ArrayList<>(this.unprocessedBlocksQueue);
  }

  /**
   * Gets beacon block headers for the validator index since last finalization
   *
   * @return
   */
  public Optional<List<BeaconBlockHeader>> getBeaconBlockHeaders(int validatorIndex) {
    return ChainStorage.get(validatorIndex, this.validatorBlockHeaders);
  }

  /**
   * Removes an unprocessed block (LIFO)
   *
   * @return
   */
  public List<Optional<BeaconBlock>> getUnprocessedBlocksUntilSlot(UnsignedLong slot) {
    List<Optional<BeaconBlock>> unprocessedBlocks = new ArrayList<>();
    boolean unproccesedBlocksLeft = true;
    Optional<BeaconBlock> currentBlock;
    while (unproccesedBlocksLeft) {
      currentBlock = ChainStorage.peek(this.unprocessedBlocksQueue);
      if (currentBlock.isPresent() && currentBlock.get().getSlot().compareTo(slot) <= 0) {
        unprocessedBlocks.add(ChainStorage.remove(this.unprocessedBlocksQueue));
      } else {
        unproccesedBlocksLeft = false;
      }
    }
    return unprocessedBlocks;
  }

  /**
   * Returns a validator's latest attestation
   *
   * @param validatorIndex
   * @return
   */
  public Optional<Attestation> getLatestAttestation(int validatorIndex) {
    Attestation attestation = latestAttestations.get(validatorIndex);
    if (attestation == null) {
      return Optional.empty();
    } else {
      return Optional.of(attestation);
    }
  }

  public ConcurrentHashMap<Bytes32, BeaconBlock> getProcessedBlockLookup() {
    return processedBlockMap;
  }

  public List<Attestation> getUnprocessedAttestationsUntilSlot(
      BeaconState state, UnsignedLong slot) {
    List<Attestation> attestations = new ArrayList<>();
    int numAttestations = 0;
    while (unprocessedAttestationsQueue.peek() != null
        && get_attestation_data_slot(state, unprocessedAttestationsQueue.peek().getData())
                .compareTo(slot)
            <= 0
        && numAttestations < Constants.MAX_ATTESTATIONS) {
      Attestation attestation = unprocessedAttestationsQueue.remove();
      // Check if attestation has already been processed in successful block
      if (!ChainStorage.get(attestation.hash_tree_root(), this.processedAttestationsMap)
          .isPresent()) {
        attestations.add(attestation);
        numAttestations++;
      }
    }
    return attestations;
  }

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

    addUnprocessedAttestation(attestation);

    // TODO: verify the assumption below:
    // ASSUMPTION: the state with which we can find the attestation participants
    // using get_attestation_participants is the state associated with the beacon
    // block being attested in the attestation.
    BeaconBlock block = processedBlockMap.get(attestation.getData().getBeacon_block_root());
    if (Objects.nonNull(block)) {
      BeaconState state = stateMap.get(block.getState_root());

      // TODO: verify attestation is stubbed out, needs to be implemented
      if (AttestationUtil.verifyAttestation(state, attestation)) {
        List<Integer> attestation_participants =
            get_attesting_indices(
                state, attestation.getData(), attestation.getAggregation_bitfield());

        for (Integer participantIndex : attestation_participants) {
          Optional<Attestation> latest_attestation = getLatestAttestation(participantIndex);
          if (!latest_attestation.isPresent()
              || get_attestation_data_slot(state, latest_attestation.get().getData())
                      .compareTo(get_attestation_data_slot(state, attestation.getData()))
                  > 0) {
            latestAttestations.put(participantIndex, attestation);
          }
        }
      }
    }
  }
}

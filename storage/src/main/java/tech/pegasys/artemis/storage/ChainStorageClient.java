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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.InvalidSSZTypeException;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.util.alogger.ALogger;

/** This class is the ChainStorage client-side logic */
public class ChainStorageClient implements ChainStorage {
  static final ALogger LOG = new ALogger(ChainStorageClient.class.getName());
  static final Integer UNPROCESSED_BLOCKS_LENGTH = 100;
  protected final ConcurrentHashMap<Integer, Attestation> latestAttestations =
      new ConcurrentHashMap<>();
  protected final PriorityBlockingQueue<BeaconBlock> unprocessedBlocks =
      new PriorityBlockingQueue<BeaconBlock>(
          UNPROCESSED_BLOCKS_LENGTH, Comparator.comparing(BeaconBlock::getSlot));
  protected final LinkedBlockingQueue<Attestation> unprocessedAttestations =
      new LinkedBlockingQueue<>();
  protected final ConcurrentHashMap<Bytes, BeaconBlock> processedBlockLookup =
      new ConcurrentHashMap<>();
  protected final ConcurrentHashMap<Bytes, BeaconState> stateLookup = new ConcurrentHashMap<>();
  protected EventBus eventBus;
  protected final Object syncObject;

  public ChainStorageClient() {
    this.syncObject = new Object();
  }

  public ChainStorageClient(EventBus eventBus) {
    this();
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  public Object getSyncObject() {
    return this.syncObject;
  }

  /**
   * Add processed block to storage
   *
   * @param blockHash
   * @param block
   */
  public void addProcessedBlock(Bytes state_root, BeaconBlock block) {
    ChainStorage.add(state_root, block, this.processedBlockLookup);
    // todo: post event to eventbus to notify the server that a new processed block has been added
  }

  /**
   * Add calculated state to storage
   *
   * @param state_root
   * @param state
   */
  public void addState(Bytes state_root, BeaconState state) {
    ChainStorage.add(state_root, state, this.stateLookup);
    // todo: post event to eventbus to notify the server that a new processed block has been added
  }

  /**
   * Add unprocessed block to storage
   *
   * @param block
   */
  public void addUnprocessedBlock(BeaconBlock block) {
    ChainStorage.add(block, this.unprocessedBlocks);
  }

  /**
   * Add unprocessed attestation to storage
   *
   * @param attestation
   */
  public void addUnprocessedAttestation(Attestation attestation) {
    ChainStorage.add(attestation, unprocessedAttestations);
  }

  /**
   * Retrieves processed block
   *
   * @param state_root
   * @return
   */
  public Optional<BeaconBlock> getProcessedBlock(Bytes state_root) {
    return ChainStorage.get(state_root, this.processedBlockLookup);
  }

  /**
   * Retrieves processed block's parent block
   *
   * @param state_root
   * @return
   */
  public Optional<BeaconBlock> getParent(BeaconBlock block) {
    Bytes parent_root = block.getParent_root();
    return this.getProcessedBlock(parent_root);
  }

  /**
   * Retrieves state
   *
   * @param state_root
   * @return
   */
  public Optional<BeaconState> getState(Bytes state_root) {
    return ChainStorage.get(state_root, this.stateLookup);
  }

  /**
   * Gets unprocessed blocks (LIFO)
   *
   * @return
   */
  public List<BeaconBlock> getUnprocessedBlocks() {
    return this.unprocessedBlocks.stream().collect(Collectors.toList());
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
      currentBlock =
          ChainStorage.<BeaconBlock, PriorityBlockingQueue<BeaconBlock>>peek(
              this.unprocessedBlocks);
      if (currentBlock.isPresent()
          && UnsignedLong.valueOf(currentBlock.get().getSlot()).compareTo(slot) <= 0) {
        unprocessedBlocks.add(
            ChainStorage.<BeaconBlock, PriorityBlockingQueue<BeaconBlock>>remove(
                this.unprocessedBlocks));
      } else {
        unproccesedBlocksLeft = false;
      }
    }
    return unprocessedBlocks;
  }

  /**
   * Removes an unprocessed attestation (LIFO)
   *
   * @return
   */
  public Optional<Attestation> getUnprocessedAttestation() {
    return ChainStorage.remove(unprocessedAttestations);
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
      return Optional.ofNullable(null);
    } else {
      return Optional.of(attestation);
    }
  }

  public ConcurrentHashMap<Bytes, BeaconBlock> getProcessedBlockLookup() {
    return processedBlockLookup;
  }

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    String ANSI_GREEN = "\u001B[32m";
    String ANSI_RESET = "\033[0m";
    LOG.log(
        Level.INFO,
        ANSI_GREEN
            + "New BeaconBlock with state root:  "
            + block.getState_root().toHexString()
            + " detected."
            + ANSI_RESET);
    addUnprocessedBlock(block);
    synchronized (syncObject) {
      syncObject.notify();
    }
  }

  @Subscribe
  public void onNewUnprocessedAttestation(Attestation attestation) {
    String ANSI_GREEN = "\u001B[32m";
    String ANSI_RESET = "\033[0m";
    LOG.log(
        Level.INFO,
        ANSI_GREEN
            + "New Attestation with block root:  "
            + attestation.getData().getBeacon_block_root()
            + " detected."
            + ANSI_RESET);
    addUnprocessedAttestation(attestation);
  }

  @Subscribe
  public void onReceievedMessage(Bytes bytes) {
    try {
      //
      Attestation attestation = Attestation.fromBytes(bytes);
      onNewUnprocessedAttestation(attestation);
    } catch (InvalidSSZTypeException e) {
      BeaconBlock block = BeaconBlock.fromBytes(bytes);
      onNewUnprocessedBlock(block);
    }
  }
}

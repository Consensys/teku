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
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import net.consensys.cava.bytes.Bytes;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;

/** This class is the ChainStorage client-side logic */
public class ChainStorageClient implements ChainStorage {

  protected final LinkedBlockingQueue<BeaconBlock> unprocessedBlocks = new LinkedBlockingQueue<>();
  protected final LinkedBlockingQueue<Attestation> unprocessedAttestations =
      new LinkedBlockingQueue<>();
  protected final HashMap<Bytes, BeaconBlock> processedBlockLookup = new HashMap<>();
  protected final HashMap<Bytes, BeaconState> stateLookup = new HashMap<>();
  protected EventBus eventBus;

  public ChainStorageClient() {}

  public ChainStorageClient(EventBus eventBus) {
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  /**
   * Add processed block to storage
   *
   * @param blockHash
   * @param block
   */
  public void addProcessedBlock(Bytes state_root, BeaconBlock block) {
    ChainStorage.<Bytes, BeaconBlock, HashMap<Bytes, BeaconBlock>>add(
        state_root, block, this.processedBlockLookup);
    LOG.info(
        "Block with state root: " + state_root.toHexString() + " has been added to ChainStorage.");
    // todo: post event to eventbus to notify the server that a new processed block has been added
  }

  /**
   * Add calculated state to storage
   *
   * @param state_root
   * @param state
   */
  public void addState(Bytes state_root, BeaconState state) {
    ChainStorage.<Bytes, BeaconState, HashMap<Bytes, BeaconState>>add(
        state_root, state, this.stateLookup);
    LOG.info(
        "Block with state root: " + state_root.toHexString() + " has been added to ChainStorage.");
    // todo: post event to eventbus to notify the server that a new processed block has been added
  }

  /**
   * Add unprocessed block to storage
   *
   * @param block
   */
  public void addUnprocessedBlock(BeaconBlock block) {
    ChainStorage.<BeaconBlock, LinkedBlockingQueue<BeaconBlock>>add(block, this.unprocessedBlocks);
  }

  /**
   * Add unprocessed attestation to storage
   *
   * @param attestation
   */
  public void addUnprocessedAttestation(Attestation attestation) {
    ChainStorage.<Attestation, LinkedBlockingQueue<Attestation>>add(
        attestation, unprocessedAttestations);
  }

  /**
   * Retrieves processed block
   *
   * @param state_root
   * @return
   */
  public Optional<BeaconBlock> getProcessedBlock(Bytes state_root) {
    return ChainStorage.<Bytes, BeaconBlock, HashMap<Bytes, BeaconBlock>>get(
        state_root, this.processedBlockLookup);
  }

  /**
   * Retrieves state
   *
   * @param state_root
   * @return
   */
  public Optional<BeaconState> getState(Bytes state_root) {
    return ChainStorage.<Bytes, BeaconState, HashMap<Bytes, BeaconState>>get(
        state_root, this.stateLookup);
  }

  /**
   * Removes an unprocessed block (LIFO)
   *
   * @return
   */
  public Optional<BeaconBlock> getUnprocessedBlock() {
    return ChainStorage.<BeaconBlock, LinkedBlockingQueue<BeaconBlock>>remove(
        this.unprocessedBlocks);
  }

  /**
   * Removes an unprocessed attestation (LIFO)
   *
   * @return
   */
  public Optional<Attestation> getUnprocessedAttestation() {
    return ChainStorage.<Attestation, LinkedBlockingQueue<Attestation>>remove(
        unprocessedAttestations);
  }

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    LOG.info("ChainStorage: new unprocessed BeaconBlock detected");
    addUnprocessedBlock(block);
  }

  @Subscribe
  public void onNewUnprocessedAttestation(Attestation attestation) {
    LOG.info("ChainStorage: new unprocessed Attestation detected");
    addUnprocessedAttestation(attestation);
  }
}

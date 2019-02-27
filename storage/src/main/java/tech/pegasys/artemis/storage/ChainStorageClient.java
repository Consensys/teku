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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import net.consensys.cava.bytes.Bytes;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;

/** This class is the ChainStorage client-side logic */
public class ChainStorageClient implements ChainStorage {

  protected BeaconBlock justified_head_block;
  protected BeaconState justified_head_state;
  protected final HashMap<Integer, Attestation> latestAttestations = new HashMap<>();
  protected final LinkedBlockingQueue<BeaconBlock> unprocessedBlocks = new LinkedBlockingQueue<>();
  protected final LinkedBlockingQueue<Attestation> unprocessedAttestations =
      new LinkedBlockingQueue<>();
  public final HashMap<Bytes, BeaconBlock> processedBlockLookup = new HashMap<>();
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
  public void onNewUnprocessedAttestation(BeaconState state, Attestation attestation) {
    LOG.info("ChainStorage: new unprocessed Attestation detected");
    addUnprocessedAttestation(attestation);

    // TODO: verify attestation is stubbed out, needs to be implemented
    if (AttestationUtil.verifyAttestation(state, attestation)) {
      List<Integer> attestation_participants =
          BeaconStateUtil.get_attestation_participants(
              state, attestation.getData(), attestation.getAggregation_bitfield().toArray());

      for (Integer participantIndex : attestation_participants) {
        Optional<Attestation> latest_attestation = getLatestAttestation(participantIndex);
        if (!latest_attestation.isPresent()
            || latest_attestation
                    .get()
                    .getData()
                    .getSlot()
                    .compareTo(attestation.getData().getSlot())
                < 0) {
          latestAttestations.put(participantIndex, attestation);
        }
      }
    }
  }

  public Optional<Attestation> getLatestAttestation(int validatorIndex) {
    Attestation attestation = latestAttestations.get(validatorIndex);
    if (attestation == null) {
      return Optional.ofNullable(null);
    } else {
      return Optional.of(attestation);
    }
  }

  public HashMap<Bytes, BeaconBlock> getProcessedBlockLookup() {
    return processedBlockLookup;
  }

  // TODO: THESE FUNCTIONS MAKE ASSUMPTIONS TO FILL THE GAPS NOT OUTLINED IN SPEC:
  // To be specific, here we assume that there can only be one justified head
  // block and state.

  // Getter for the second argument of lmd_ghost
  public BeaconState get_justified_head_state() {
    return justified_head_state;
  }

  // Getter for the third argument of lmd_ghost
  public BeaconBlock get_justified_head_block() {
    return justified_head_block;
  }

  public void setJustifiedHead(BeaconState state, BeaconBlock block) {
    justified_head_state = state;
    justified_head_block = block;
  }
}

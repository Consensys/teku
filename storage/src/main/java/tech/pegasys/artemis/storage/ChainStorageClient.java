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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSSignature;

/** This class is the ChainStorage client-side logic */
public class ChainStorageClient implements ChainStorage {
  private static final ALogger STDOUT = new ALogger("stdout");
  static final ALogger LOG = new ALogger(ChainStorageClient.class.getName());
  static final Integer UNPROCESSED_BLOCKS_LENGTH = 100;
  protected final ConcurrentHashMap<Integer, Attestation> latestAttestations =
      new ConcurrentHashMap<>();
  protected final PriorityBlockingQueue<BeaconBlock> unprocessedBlocks =
      new PriorityBlockingQueue<>(
          UNPROCESSED_BLOCKS_LENGTH, Comparator.comparing(BeaconBlock::getSlot));
  protected final LinkedBlockingQueue<Attestation> unprocessedAttestations =
      new LinkedBlockingQueue<>();
  protected final ConcurrentHashMap<Bytes, BeaconBlock> processedBlockLookup =
      new ConcurrentHashMap<>();
  protected final ConcurrentHashMap<Bytes, BeaconState> stateLookup = new ConcurrentHashMap<>();
  protected final ConcurrentHashMap<BLSSignature, Attestation> processedAttestations =
      new ConcurrentHashMap<>();
  private final PriorityBlockingQueue<Attestation> attestationsQueue =
      new PriorityBlockingQueue<>(
          UNPROCESSED_BLOCKS_LENGTH, Comparator.comparing(Attestation::getSlot));
  private final ConcurrentHashMap<Integer, List<BeaconBlockHeader>> validatorBlockHeaders =
      new ConcurrentHashMap<>();
  protected EventBus eventBus;

  public ChainStorageClient() {}

  public ChainStorageClient(EventBus eventBus) {
    this();
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  /**
   * Add processed block to storage
   *
   * @param state_root
   * @param block
   */
  public void addProcessedBlock(Bytes state_root, BeaconBlock block) {
    ChainStorage.add(state_root, block, this.processedBlockLookup);
    // todo: post event to eventbus to notify the server that a new processed block has been added
  }

  /**
   * Add processed block to storage
   *
   * @param attestation
   */
  public void addProcessedAttestation(Attestation attestation) {
    ChainStorage.add(attestation.getAggregate_signature(), attestation, this.processedAttestations);
  }

  /**
   * Add calculated state to storage
   *
   * @param state_root
   * @param state
   */
  public void addState(Bytes state_root, BeaconState state) {
    ChainStorage.add(state_root, state, this.stateLookup);
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
    ChainStorage.add(attestation, attestationsQueue);
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
   * @param block
   * @return
   */
  public Optional<BeaconBlock> getParent(BeaconBlock block) {
    Bytes parent_root = block.getPrevious_block_root();
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
    return new ArrayList<>(this.unprocessedBlocks);
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
  public List<Optional<BeaconBlock>> getUnprocessedBlocksUntilSlot(long slot) {
    List<Optional<BeaconBlock>> unprocessedBlocks = new ArrayList<>();
    boolean unproccesedBlocksLeft = true;
    Optional<BeaconBlock> currentBlock;
    while (unproccesedBlocksLeft) {
      currentBlock = ChainStorage.peek(this.unprocessedBlocks);
      if (currentBlock.isPresent() && currentBlock.get().getSlot() <= slot) {
        unprocessedBlocks.add(ChainStorage.remove(this.unprocessedBlocks));
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
      return Optional.empty();
    } else {
      return Optional.of(attestation);
    }
  }

  public ConcurrentHashMap<Bytes, BeaconBlock> getProcessedBlockLookup() {
    return processedBlockLookup;
  }

  public List<Attestation> getUnprocessedAttestationsUntilSlot(UnsignedLong slot) {
    List<Attestation> attestations = new ArrayList<>();
    int numAttestations = 0;
    while (attestationsQueue.peek() != null
        && attestationsQueue.peek().getSlot().compareTo(slot) <= 0
        && numAttestations < Constants.MAX_ATTESTATIONS) {
      Attestation attestation = attestationsQueue.remove();
      // Check if attestation has already been processed in successful block
      if (!ChainStorage.get(attestation.getAggregate_signature(), this.processedAttestations)
          .isPresent()) {
        attestations.add(attestation);
        numAttestations++;
      }
    }
    return attestations;
  }

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    String ANSI_GREEN = "\u001B[32m";
    String ANSI_RESET = "\033[0m";
    STDOUT.log(
        Level.INFO,
        ANSI_GREEN
            + "New BeaconBlock with state root:  "
            + block.getState_root().toHexString()
            + " detected."
            + ANSI_RESET);
    addUnprocessedBlock(block);
  }

  @Subscribe
  public void onNewUnprocessedAttestation(Attestation attestation) {
    String ANSI_GREEN = "\u001B[32m";
    String ANSI_RESET = "\033[0m";
    STDOUT.log(
        Level.INFO,
        ANSI_GREEN
            + "New Attestation with block root:  "
            + attestation.getData().getBeacon_block_root()
            + " detected."
            + ANSI_RESET);

    addUnprocessedAttestation(attestation);

    // TODO: verify the assumption below:
    // ASSUMPTION: the state with which we can find the attestation participants
    // using get_attestation_participants is the state associated with the beacon
    // block being attested in the attestation.
    BeaconBlock block = processedBlockLookup.get(attestation.getData().getBeacon_block_root());
    if (Objects.nonNull(block)) {
      BeaconState state = stateLookup.get(block.getState_root());

      // TODO: verify attestation is stubbed out, needs to be implemented
      if (AttestationUtil.verifyAttestation(state, attestation)) {
        List<Integer> attestation_participants =
            BeaconStateUtil.get_attestation_participants(
                state, attestation.getData(), attestation.getAggregation_bitfield());

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
  }
}

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

package tech.pegasys.artemis.statetransition;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import net.consensys.cava.bytes.Bytes32;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.pow.api.ChainStartEvent;
import tech.pegasys.artemis.pow.api.ValidatorRegistrationEvent;
import tech.pegasys.artemis.storage.ChainStorage;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

/** Class to manage the state tree and initiate state transitions */
public class StateTreeManager {

  private BeaconBlock head;
  private UnsignedLong nodeTime;
  private UnsignedLong nodeSlot;
  private final EventBus eventBus;
  private StateTransition stateTransition;
  private ChainStorageClient store;
  private static final Logger LOG = LogManager.getLogger(StateTreeManager.class.getName());

  public StateTreeManager(EventBus eventBus) {
    this.eventBus = eventBus;
    this.stateTransition = new StateTransition();
    this.eventBus.register(this);
    this.store = ChainStorage.Create(ChainStorageClient.class, eventBus);

    try {
      BeaconState initial_state = DataStructureUtil.createInitialBeaconState();
      Bytes32 initial_state_root = HashTreeUtil.hash_tree_root(initial_state.toBytes());
      BeaconBlock genesis_block = BeaconBlock.createGenesis(initial_state_root);
      Bytes32 genesis_block_root = HashTreeUtil.hash_tree_root(genesis_block.toBytes());
      LOG.info("Initial State:");
      LOG.info("  initial state root is " + initial_state_root.toHexString());
      this.store.addState(initial_state_root, initial_state);
      this.store.addProcessedBlock(genesis_block_root, genesis_block);
      this.store.setJustifiedHead(initial_state, genesis_block);
    } catch (IllegalStateException e) {
      LOG.fatal(e);
    }
  }

  @Subscribe
  public void onChainStarted(ChainStartEvent event) {
    LOG.info("******* ChainStart Event Detected *******");
    this.nodeSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    this.nodeTime =
        UnsignedLong.valueOf(Constants.GENESIS_SLOT)
            .times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT));
    LOG.info("node slot: " + nodeSlot.longValue());
    LOG.info("node time: " + nodeTime.longValue());
    boolean result = true;
    this.eventBus.post(result);
  }

  @Subscribe
  public void onValidatorRegistered(ValidatorRegistrationEvent event) {
    LOG.info("Validator Registration Event detected");
    LOG.info("   Validator Number: " + event.getResponse().log.toString());
  }

  @Subscribe
  public void onNewSlot(Date date) throws StateTransitionException {
    this.nodeSlot = this.nodeSlot.plus(UnsignedLong.ONE);
    this.nodeTime = this.nodeTime.plus(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT));

    LOG.info("******* Slot Event Detected *******");
    LOG.info("node time: " + nodeTime.longValue());
    LOG.info("node slot: " + nodeSlot.longValue());

    List<Optional<BeaconBlock>> unprocessedBlocks =
        this.store.getUnprocessedBlocksUntilSlot(nodeSlot);
    unprocessedBlocks.forEach((block) -> processFork(block));

    // Run lmd_ghost to get the head
    try {
      this.head =
          LmdGhost.lmd_ghost(
              store, store.get_justified_head_state(), store.get_justified_head_block());
    } catch (StateTransitionException e) {
      LOG.fatal(e);
    }

    // Run state transition from the new head to node.slot
    Bytes32 head_block_root = this.head.getState_root();
    BeaconState newState = store.getState(head_block_root).get();
    Bytes32 newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
    boolean firstLoop = true;
    while (newState.getSlot().compareTo(nodeSlot) < 0) {
      if (firstLoop) {
        LOG.info("Transitioning state from slot: " + newState.getSlot() + " to slot: " + nodeSlot);
        firstLoop = false;
      }
      newState = BeaconState.deepCopy(newState);
      stateTransition.initiate(newState, null, store);
      newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
      this.store.addState(newStateRoot, newState);
    }
    LOG.info("latest head block root: " + head_block_root);
    LOG.info("latest head state root: " + newStateRoot.toHexString());
  }

  protected Boolean inspectBlock(Optional<BeaconBlock> block) {
    if (!block.isPresent()) {
      return false;
    }
    if (!this.store.getParent(block.get()).isPresent()) {
      return false;
    }
    UnsignedLong blockTime =
        UnsignedLong.valueOf(block.get().getSlot())
            .times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT));
    // TODO: Here we reject block because time is not there,
    // however, the block is already removed from queue, so
    // we're losing a valid block here.
    if (this.nodeTime.compareTo(blockTime) < 0) {
      return false;
    }
    return true;
  }

  protected void processFork(Optional<BeaconBlock> unprocessedBlock) {
    try {
      Boolean shouldProcessBlock = inspectBlock(unprocessedBlock);
      if (shouldProcessBlock) {

        // Get block, block root and block state root
        BeaconBlock block = unprocessedBlock.get();
        Bytes32 blockStateRoot = block.getState_root();
        Bytes32 blockRoot = HashTreeUtil.hash_tree_root(block.toBytes());

        // Get parent block and parent block state root
        BeaconBlock parentBlock = this.store.getParent(block).get();
        Bytes32 parentBlockStateRoot = parentBlock.getState_root();

        // Get parent block state
        BeaconState parentState = this.store.getState(parentBlockStateRoot).get();

        LOG.info("parent fork_head slot: " + parentState.getSlot());
        LOG.info("parent fork_head state root: " + parentBlockStateRoot.toHexString());
        LOG.info("fork_head slot: " + block.getSlot());
        LOG.info("fork_head state root: " + blockStateRoot.toHexString());

        BeaconState currentState = BeaconState.deepCopy(parentState);

        // TODO: check if the fork_head's parent slot is further back than the weak subjectivity
        // period, should we check?

        // Run state transition from block’s parentState and parent’s slot with no blocks until
        // block.slot - 1
        Bytes32 currentStateRoot;
        boolean firstLoop = true;
        while (currentState.getSlot().compareTo(UnsignedLong.valueOf(block.getSlot() - 1)) < 0) {
          if (firstLoop) {
            LOG.info(
                "Transitioning state from slot: "
                    + currentState.getSlot()
                    + " to slot: "
                    + UnsignedLong.valueOf(block.getSlot() - 1));
            firstLoop = false;
          }
          stateTransition.initiate(currentState, null, store);
          currentStateRoot = HashTreeUtil.hash_tree_root(currentState.toBytes());
          this.store.addState(currentStateRoot, currentState);
          currentState = BeaconState.deepCopy(currentState);
        }

        // Run state transition using the block
        stateTransition.initiate(currentState, block, store);
        currentStateRoot = HashTreeUtil.hash_tree_root(currentState.toBytes());

        // Verify that the state root we have computed is the state root that block is
        // claiming us we should reach, save the block and the state if its correct.
        if (blockStateRoot.equals(currentStateRoot)) {
          LOG.info("The fork_head's state root matches the calculated state root!");
          LOG.info("  new state root: " + currentStateRoot.toHexString());
          LOG.info("  fork_head state root: " + blockStateRoot.toHexString());
          // TODO: storing fork_head and state together as a tuple would be more convenient
          this.store.addProcessedBlock(blockStateRoot, block);
          this.store.addProcessedBlock(blockRoot, block);
          this.store.addState(currentStateRoot, currentState);
        }
      }
    } catch (NoSuchElementException | IllegalArgumentException | StateTransitionException e) {
      LOG.warn(e);
    }
  }
}

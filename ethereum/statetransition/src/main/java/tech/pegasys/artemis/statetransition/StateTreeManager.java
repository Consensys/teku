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

  private BeaconState canonical_state;
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
  }

  @Subscribe
  public void onChainStarted(ChainStartEvent event) {
    LOG.info("******* ChainStart Event Detected *******");
    this.nodeSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    this.nodeTime =
        UnsignedLong.valueOf(Constants.GENESIS_SLOT)
            .times(UnsignedLong.valueOf(Constants.SLOT_DURATION));
    LOG.info("node time: " + nodeTime.longValue());
    Boolean result = false;
    try {
      BeaconState initial_state = DataStructureUtil.createInitialBeaconState();
      Bytes32 initial_state_root = HashTreeUtil.hash_tree_root(initial_state.toBytes());
      BeaconBlock genesis_block = BeaconBlock.createGenesis(initial_state_root);
      Bytes32 genesis_block_root = HashTreeUtil.hash_tree_root(genesis_block.toBytes());
      LOG.info("Initial State:");
      LOG.info("  initial state root is " + initial_state_root.toHexString());
      this.store.addState(initial_state_root, initial_state);
      this.store.addProcessedBlock(initial_state_root, genesis_block);
      this.store.addProcessedBlock(genesis_block_root, genesis_block);
      this.canonical_state = initial_state;
      result = true;
    } catch (IllegalStateException e) {
      LOG.fatal(e);
    } finally {
      this.eventBus.post(result);
    }
  }

  @Subscribe
  public void onValidatorRegistered(ValidatorRegistrationEvent event) {
    LOG.info("Validator Registration Event detected");
    LOG.info("   Validator Number: " + event.getResponse().log.toString());
  }

  @Subscribe
  public void onNewSlot(Date date) {
    this.nodeSlot = this.nodeSlot.plus(UnsignedLong.ONE);
    this.nodeTime = this.nodeTime.plus(UnsignedLong.valueOf(Constants.SLOT_DURATION));

    LOG.info("******* Slot Event Detected *******");
    LOG.info("node time: " + nodeTime.longValue());
    LOG.info("node slot: " + nodeSlot.longValue());

    Optional<BeaconBlock> block = this.store.getUnprocessedBlock();
    processFork(this.nodeSlot, block);
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
            .times(UnsignedLong.valueOf(Constants.SLOT_DURATION));
    // TODO: Here we reject block because time is not there,
    // however, the block is already removed from queue, so
    // we're losing a valid block here.
    if (this.nodeTime.compareTo(blockTime) < 0) {
      return false;
    }
    return true;
  }

  protected void processFork(UnsignedLong current_slot, Optional<BeaconBlock> fork_head) {
    try {
      BeaconState newState = null;
      Bytes32 newStateRoot = Bytes32.ZERO;
      Boolean shouldProcessBlock = inspectBlock(fork_head);
      if (shouldProcessBlock) {
        Bytes32 blockStateRoot = fork_head.get().getState_root();
        Bytes32 blockRoot = HashTreeUtil.hash_tree_root(fork_head.get().toBytes());
        BeaconBlock parentBlock = this.store.getParent(fork_head.get()).get();
        Bytes32 parentBlockStateRoot = parentBlock.getState_root();
        // get state corresponding to the parent fork_head
        BeaconState parentState = this.store.getState(parentBlockStateRoot).get();
        LOG.info("parent fork_head slot: " + parentState.getSlot());
        LOG.info("parent fork_head state root: " + parentBlockStateRoot.toHexString());
        LOG.info("fork_head slot: " + fork_head.get().getSlot());
        LOG.info("fork_head state root: " + blockStateRoot.toHexString());
        newState = BeaconState.deepCopy(parentState);

        // TODO: check if the fork_head's parent slot is further back than the weak subjectivity
        // period, should we check?
        // run stateTransition.initiate() on empty slots from parentBlock.slot to fork_head.slot-1
        int counter = 0;
        while (newState.getSlot().compareTo(UnsignedLong.valueOf(fork_head.get().getSlot() - 1))
            < 0) {
          if (counter == 0) {
            LOG.info(
                "Transitioning state from slot: "
                    + newState.getSlot()
                    + " to slot: "
                    + UnsignedLong.valueOf(fork_head.get().getSlot() - 1));
          }
          stateTransition.initiate(newState, null, store);
          newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
          this.store.addState(newStateRoot, newState);
          newState = BeaconState.deepCopy(newState);
          counter++;
        }

        // run stateTransition.initiate() on fork_head.slot
        stateTransition.initiate(newState, fork_head.get(), store);
        newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());

        // state root verification
        if (blockStateRoot.equals(newStateRoot)) {
          LOG.info("The fork_head's state root matches the calculated state root!");
          LOG.info("  new state root: " + newStateRoot.toHexString());
          LOG.info("  fork_head state root: " + blockStateRoot.toHexString());
          // TODO: storing fork_head and state together as a tuple would be more convenient
          this.store.addProcessedBlock(blockStateRoot, fork_head.get());
          this.store.addProcessedBlock(blockRoot, fork_head.get());
          this.store.addState(newStateRoot, newState);

          // run stateTransition.initiate() on slots from fork_head.slot to node.slot
          counter = 0;
          while (newState.getSlot().compareTo(nodeSlot) < 0) {
            if (counter == 0) {
              LOG.info(
                  "Transitioning state from slot: " + newState.getSlot() + " to slot: " + nodeSlot);
            }
            newState = BeaconState.deepCopy(newState);
            stateTransition.initiate(newState, null, store);
            newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
            this.store.addState(newStateRoot, newState);
            counter++;
          }
          LOG.info("latest state root: " + newStateRoot.toHexString());
          this.canonical_state = newState;
        } else {
          LOG.info("The fork_head's state root does not match the calculated state root!");
          LOG.info("  new state root: " + newStateRoot.toHexString());
          LOG.info("  fork_head state root: " + blockStateRoot.toHexString());
          shouldProcessBlock = false;
        }
      }
      // this conditional evaluates true if:
      // 1. inspectBlock returns false
      // 2. state root verification fails on a fork_head
      if (!shouldProcessBlock) {
        newState = BeaconState.deepCopy(this.canonical_state);
        stateTransition.initiate(newState, null, store);
        newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
        this.store.addState(newStateRoot, newState);
        LOG.info("latest state root: " + newStateRoot.toHexString());
        this.canonical_state = newState;
      }
    } catch (NoSuchElementException | IllegalArgumentException | StateTransitionException e) {
      LOG.warn(e);
    }
  }
}

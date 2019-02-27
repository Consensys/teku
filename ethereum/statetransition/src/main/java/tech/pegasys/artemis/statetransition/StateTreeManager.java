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

import static java.util.Objects.requireNonNull;

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
    this.nodeTime = this.nodeTime.plus(UnsignedLong.valueOf(Constants.SLOT_DURATION));
    UnsignedLong nodeSlot = nodeTime.dividedBy(UnsignedLong.valueOf(Constants.SLOT_DURATION));
    LOG.info("******* Slot Event Detected *******");
    LOG.info("node time: " + nodeTime.longValue());
    LOG.info("node slot: " + nodeSlot.longValue());

    BeaconState newState = null;
    Bytes32 newStateRoot = Bytes32.ZERO;
    Optional<BeaconBlock> block = this.store.getUnprocessedBlock();
    try {
      if (shouldProcessBlock(block)) {
        Bytes32 blockStateRoot = block.get().getState_root();
        Bytes32 blockRoot = HashTreeUtil.hash_tree_root(block.get().toBytes());
        BeaconBlock parentBlock = this.store.getParent(block.get()).get();
        Bytes32 parentBlockStateRoot = parentBlock.getState_root();
        // get state corresponding to the parent block
        BeaconState parentState = this.store.getState(parentBlockStateRoot).get();
        LOG.info("parent block slot: " + parentState.getSlot());
        LOG.info("parent block state root: " + parentBlockStateRoot.toHexString());
        LOG.info("block slot: " + block.get().getSlot());
        LOG.info("block state root: " + blockStateRoot.toHexString());
        newState = BeaconState.deepCopy(parentState);

        // run stateTransition.initiate() on empty slots from parentBlock.slot to block.slot-1
        int counter = 0;
        while (newState.getSlot().compareTo(UnsignedLong.valueOf(block.get().getSlot() - 1)) < 0) {
          if (counter == 0) {
            LOG.info(
                "Transitioning state from slot: "
                    + newState.getSlot()
                    + " to slot: "
                    + UnsignedLong.valueOf(block.get().getSlot() - 1));
          }
          stateTransition.initiate(newState, null);
          newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
          this.store.addState(newStateRoot, newState);
          newState = BeaconState.deepCopy(newState);
          counter++;
        }

        // run stateTransition.initiate() on block.slot
        stateTransition.initiate(newState, block.get());
        newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());

        // state root verification
        if (blockStateRoot.equals(newStateRoot)) {
          LOG.info("The block's state root matches the calculated state root!");
          LOG.info("  new state root: " + newStateRoot.toHexString());
          LOG.info("  block state root: " + blockStateRoot.toHexString());
          // TODO: storing block and state together as a tuple would be more convenient
          this.store.addProcessedBlock(blockStateRoot, block.get());
          this.store.addProcessedBlock(blockRoot, block.get());
          this.store.addState(newStateRoot, newState);

          // run stateTransition.initiate() on slots from block.slot to node.slot
          counter = 0;
          while (newState.getSlot().compareTo(nodeSlot) < 0) {
            if (counter == 0) {
              LOG.info(
                  "Transitioning state from slot: " + newState.getSlot() + " to slot: " + nodeSlot);
            }
            newState = BeaconState.deepCopy(newState);
            stateTransition.initiate(newState, null);
            newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
            this.store.addState(newStateRoot, newState);
            counter++;
          }
          LOG.info("latest state root: " + newStateRoot.toHexString());
          this.canonical_state = newState;
        } else {
          LOG.info("The block's state root does not match the calculated state root!");
          LOG.info("  new state root: " + newStateRoot.toHexString());
          LOG.info("  block state root: " + blockStateRoot.toHexString());
        }
      } else {
        newState = BeaconState.deepCopy(this.canonical_state);
        stateTransition.initiate(newState, null);
        newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
        this.store.addState(newStateRoot, newState);
        LOG.info("latest state root: " + newStateRoot.toHexString());
        this.canonical_state = newState;
      }
    } catch (NoSuchElementException | IllegalArgumentException | StateTransitionException e) {
      LOG.warn(e);
    }
  }

  protected Boolean shouldProcessBlock(Optional<BeaconBlock> block) {
    if (!block.isPresent()) {
      return false;
    }
    if (!this.store.getParent(block.get()).isPresent()) {
      return false;
    }
    UnsignedLong blockTime =
        UnsignedLong.valueOf(block.get().getSlot())
            .times(UnsignedLong.valueOf(Constants.SLOT_DURATION));
    if (this.nodeTime.compareTo(blockTime) < 0) {
      return false;
    }
    return true;
  }

  /*
   * Get the ancestor of ``block`` with slot number ``slot``; return ``None`` if not found.
   */
  public Optional<BeaconBlock> get_ancestor(BeaconBlock block, UnsignedLong slotNumber) {
    requireNonNull(block);
    UnsignedLong blockSlot = UnsignedLong.valueOf(block.getSlot());
    if (blockSlot.compareTo(slotNumber) == 0) {
      return Optional.of(block);
    } else if (blockSlot.compareTo(slotNumber) < 0) {
      return Optional.ofNullable(null);
    } else {
      return get_ancestor(this.store.getParent(block).get(), slotNumber);
    }
  }
}

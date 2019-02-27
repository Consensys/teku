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

import static com.google.common.base.Preconditions.checkArgument;
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
  private UnsignedLong clockTime;
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
    LOG.info("******* ChainStart Event Detected *******\n");
    Boolean result = false;
    try {
      BeaconState initial_state = DataStructureUtil.createInitialBeaconState();
      Bytes32 initial_state_root = HashTreeUtil.hash_tree_root(initial_state.toBytes());
      this.clockTime =
          UnsignedLong.valueOf(Constants.GENESIS_SLOT)
              .times(UnsignedLong.valueOf(Constants.SLOT_DURATION));
      LOG.info("Initial State:");
      LOG.info("  initial state root is " + initial_state_root.toHexString());
      LOG.info("  local clock time: " + clockTime.longValue());
      this.store.addState(initial_state_root, initial_state);
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
    LOG.info("\n******* Slot Event Detected *******\n");
    BeaconState newState = null;
    Optional<BeaconBlock> block = this.store.getUnprocessedBlock();
    try {
      if (block.isPresent()) {
        Bytes32 blockStateRoot = block.get().getState_root();
        Bytes32 blockRoot = HashTreeUtil.hash_tree_root(block.get().toBytes());

        LOG.info("Retrieved new block:");
        LOG.info("  block slot: " + block.get().getSlot());
        LOG.info("  block state root: " + blockStateRoot.toHexString());

        Optional<BeaconBlock> parentBlock = this.store.getParent(block.get());
        if (parentBlock.isPresent()) {
          Bytes32 parentBlockStateRoot = parentBlock.get().getState_root();

          // get state corresponding to the parent block
          BeaconState parentState = this.store.getState(parentBlockStateRoot).get();
          LOG.info("  state slot: " + parentState.getSlot());
          LOG.info("  state root: " + parentBlockStateRoot.toHexString());
          newState = BeaconState.deepCopy(parentState);
          Bytes32 newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
          LOG.info("Begin state transition:");

          // process empty slots
          while (newState.getSlot().compareTo(UnsignedLong.valueOf(block.get().getSlot() - 1))
              < 0) {
            LOG.info(
                "newState slot: " + newState.getSlot() + " block slot: " + block.get().getSlot());
            stateTransition.initiate(newState, null);
            newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
            LOG.info("  state slot: " + newState.getSlot());
            LOG.info("  state root: " + newStateRoot.toHexString());
            this.store.addState(newStateRoot, newState);
            newState = BeaconState.deepCopy(newState);
          }

          stateTransition.initiate(newState, block.get());
          newStateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
          LOG.info("  new state root: " + newStateRoot.toHexString());
          LOG.info("  block state root: " + blockStateRoot.toHexString());

          // state root verification
          checkArgument(
              blockStateRoot.equals(newStateRoot),
              "The block's state root %s does not match the calculated state root %s!",
              blockStateRoot.toHexString(),
              newStateRoot.toHexString());
          // TODO: storing block and state together as a tuple would be more convenient
          this.store.addState(newStateRoot, newState);
          this.store.addProcessedBlock(blockStateRoot, block.get());
          this.store.addProcessedBlock(blockRoot, block.get());
        } else {
          LOG.info(
              "Receieved genesis block.  Checking to see if state root matches this clients initial state root");
          Optional<BeaconState> state = this.store.getState(block.get().getState_root());
          if (state.isPresent()) {
            LOG.info("State roots match! Saving genesis block in storage");
            this.store.addProcessedBlock(blockStateRoot, block.get());
            this.store.addProcessedBlock(blockRoot, block.get());
            newState = BeaconState.deepCopy(state.get());
          }
        }
      } else {
        // TODO: If there is no block for the current slot, should i just get the canonical state?
        newState = BeaconState.deepCopy(this.canonical_state);
        stateTransition.initiate(newState, null);
      }
      this.canonical_state = newState;
      Bytes32 stateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
      this.store.addState(stateRoot, newState);
    } catch (NoSuchElementException | IllegalArgumentException | StateTransitionException e) {
      LOG.warn(e);
    }
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

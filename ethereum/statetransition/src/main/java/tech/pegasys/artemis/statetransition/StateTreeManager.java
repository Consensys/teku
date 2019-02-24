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
    LOG.info("ChainStart Event Detected");
    Boolean result = false;
    try {
      BeaconState initial_state = DataStructureUtil.createInitialBeaconState();
      Bytes32 initial_state_root = HashTreeUtil.hash_tree_root(initial_state.toBytes());
      LOG.info("OnChainStart: initial state root is " + initial_state_root.toHexString());
      this.store.addState(initial_state_root, initial_state);
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
    LOG.info("****** New Slot at: " + date + " ******");
    Optional<BeaconBlock> block = this.store.getUnprocessedBlock();
    if (block.isPresent()) {
      Bytes32 blockStateRoot = block.get().getState_root();
      try {
        Optional<BeaconBlock> parentBlock = this.store.getParent(block.get());
        Bytes32 blockRoot = HashTreeUtil.hash_tree_root(block.get().toBytes());
        if (parentBlock.isPresent()) {
          // get state corresponding to the parent block
          Optional<BeaconState> state = this.store.getState(parentBlock.get().getState_root());
          BeaconState newState = BeaconState.deepCopy(state.get());
          stateTransition.initiate(newState, block.get());
          Bytes32 stateRoot = HashTreeUtil.hash_tree_root(newState.toBytes());
          LOG.info("New state root: " + stateRoot.toHexString());
          // state root verification
          checkArgument(
              blockStateRoot.equals(stateRoot),
              "The block's state root %s does not match the calculated state root %s!",
              blockStateRoot.toHexString(),
              stateRoot.toHexString());
          // TODO: storing block and state together as a tuple would be more convenient
          this.store.addState(stateRoot, newState);
          this.store.addProcessedBlock(blockStateRoot, block.get());
          this.store.addProcessedBlock(blockRoot, block.get());
        } else {
          LOG.info(
              "Receieved a block without a parent.  Checking to see if state root matches this clients initial state root");
          Optional<BeaconState> state = this.store.getState(block.get().getState_root());
          if (state.isPresent()) {
            LOG.info("State roots match! Saving genesis block in storage");
            this.store.addProcessedBlock(blockStateRoot, block.get());
            this.store.addProcessedBlock(blockRoot, block.get());
          }
        }

      } catch (NoSuchElementException | IllegalArgumentException | StateTransitionException e) {
        LOG.warn(e);
      }
    } else {
      // TODO: If there is no block for the current slot, should i just get the canonical state?
      // stateTransition.initiate(state, null);
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

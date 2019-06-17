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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1.PublicKey;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.event.Eth2Genesis;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.BeaconBlockUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.pow.api.Eth2GenesisEvent;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

/** Class to manage the state tree and initiate state transitions */
public class StateProcessor {

  private BeaconState headState; // state chosen by lmd ghost to build and attest on
  private BeaconBlock headBlock; // block chosen by lmd ghost to build and attest on
  private Bytes32 finalizedStateRoot; // most recent finalized state root
  private Bytes32 finalizedBlockRoot; // most recent finalized block root
  private UnsignedLong finalizedEpoch; // most recent finalized epoch
  private Bytes32 justifiedStateRoot; // most recent justified state root
  private Bytes32 currentJustifiedBlockRoot; // most recent justified block root
  private UnsignedLong nodeTime;
  private UnsignedLong nodeSlot;
  private final EventBus eventBus;
  private final StateTransition stateTransition;
  private ChainStorageClient store;
  private ArtemisConfiguration config;
  private PublicKey publicKey;
  private static final ALogger STDOUT = new ALogger("stdout");
  private static final ALogger LOG = new ALogger(StateProcessor.class.getName());
  private List<Deposit> deposits;

  // Colors
  public static final String ANSI_RESET = "\u001B[0m";
  public static final String ANSI_RED = "\u001B[31m";
  public static final String ANSI_PURPLE = "\u001B[35m";
  public static final String ANSI_WHITE_BOLD = "\033[1;30m";

  public StateProcessor(ServiceConfig config, ChainStorageClient store) {
    this.eventBus = config.getEventBus();
    this.config = config.getConfig();
    this.publicKey = config.getKeyPair().publicKey();
    this.stateTransition = new StateTransition(true);
    this.store = store;
    this.eventBus.register(this);
  }

  @Subscribe
  public void onEth2GenesisEvent(Eth2GenesisEvent event) {
    STDOUT.log(
        Level.INFO,
        "******* Eth2Genesis Event detected ******* : "
            + ((Eth2Genesis) event).getDeposit_root().toString()
            + ANSI_RESET);
    this.nodeSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    this.nodeTime =
        UnsignedLong.valueOf(Constants.GENESIS_SLOT)
            .times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT));
    STDOUT.log(Level.INFO, "Node slot: " + nodeSlot);
    STDOUT.log(Level.INFO, "Node time: " + nodeTime);
    try {
      BeaconState initial_state;
      if (config.getDepositMode().equals(Constants.DEPOSIT_TEST))
        initial_state = DataStructureUtil.createInitialBeaconState(config.getNumValidators());
      else {
        deposits = DepositUtil.generateBranchProofs(deposits);
        initial_state =
            DataStructureUtil.createInitialBeaconState(
                deposits, ((Eth2Genesis) event).getDeposit_root());
      }
      Bytes32 initial_state_root = initial_state.hash_tree_root();
      BeaconBlock genesis_block = BeaconBlockUtil.get_empty_block();
      genesis_block.setState_root(initial_state_root);
      Bytes32 genesis_block_root = genesis_block.signed_root("signature");
      STDOUT.log(Level.INFO, "Initial state root is " + initial_state_root.toHexString());
      this.store.addState(initial_state_root, initial_state);
      this.store.addProcessedBlock(genesis_block_root, genesis_block);
      this.headBlock = genesis_block;
      this.justifiedStateRoot = initial_state_root;
      this.currentJustifiedBlockRoot = genesis_block_root;
      this.finalizedStateRoot = initial_state_root;
      this.finalizedBlockRoot = genesis_block_root;
      this.finalizedEpoch = initial_state.getFinalized_epoch();
      this.eventBus.post(
          new GenesisHeadStateEvent((BeaconStateWithCache) initial_state, genesis_block));
    } catch (IllegalStateException e) {
      LOG.log(Level.FATAL, e.toString());
    }
  }

  @Subscribe
  public void onDeposit(Deposit deposit) {
    if (deposits == null) deposits = new ArrayList<Deposit>();
    deposits.add(deposit);
  }

  @Subscribe
  public void onNewSlot(Date date) throws StateTransitionException, InterruptedException {
    this.nodeSlot = this.nodeSlot.plus(UnsignedLong.ONE);
    this.nodeTime = this.nodeTime.plus(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT));

    STDOUT.log(Level.INFO, ANSI_WHITE_BOLD + "******* Slot Event *******" + ANSI_RESET);
    STDOUT.log(Level.INFO, "Node time:                             " + nodeTime);
    STDOUT.log(
        Level.INFO,
        "Node slot:                             "
            + nodeSlot
            + "  |  "
            + nodeSlot.longValue() % Constants.GENESIS_SLOT);

    Thread.sleep(3000);
    // Get all the unprocessed blocks that are for slots <= nodeSlot
    List<Optional<BeaconBlock>> unprocessedBlocks =
        this.store.getUnprocessedBlocksUntilSlot(nodeSlot.longValue());

    // Use each block to build on all possible forks
    unprocessedBlocks.forEach((block) -> processBlock(block));

    // Update the block that is subjectively the head of the chain  using lmd_ghost
    STDOUT.log(Level.INFO, ANSI_PURPLE + "Updating head block using LMDGhost." + ANSI_RESET);
    updateHeadBlockUsingLMDGhost();
    STDOUT.log(
        Level.INFO,
        "Head block slot:                      "
            + headBlock.getSlot()
            + "  |  "
            + headBlock.getSlot() % Constants.GENESIS_SLOT);

    // Get head block's state, and initialize a newHeadState variable to run state transition on
    BeaconState headBlockState = store.getState(headBlock.getState_root()).get();
    Long justifiedEpoch = headBlockState.getCurrent_justified_epoch().longValue();
    Long finalizedEpoch = headBlockState.getFinalized_epoch().longValue();
    STDOUT.log(
        Level.INFO,
        "Justified block epoch:                 "
            + justifiedEpoch
            + "  |  "
            + justifiedEpoch % Constants.GENESIS_EPOCH);
    STDOUT.log(
        Level.INFO,
        "Finalized block epoch:                 "
            + finalizedEpoch
            + "  |  "
            + finalizedEpoch % Constants.GENESIS_EPOCH);

    BeaconStateWithCache newHeadState =
        BeaconStateWithCache.deepCopy((BeaconStateWithCache) headBlockState);

    // Run state transition with no blocks from the newHeadState.slot to node.slot
    boolean firstLoop = true;
    while (newHeadState.getSlot().compareTo(nodeSlot) < 0) {
      if (firstLoop) {
        STDOUT.log(
            Level.INFO,
            "Transitioning state from slot: " + newHeadState.getSlot() + " to slot: " + nodeSlot);
        firstLoop = false;
      }
      stateTransition.initiate(newHeadState, null);
    }
    this.headState = newHeadState;
    this.store.addState(newHeadState.hash_tree_root(), newHeadState);

    // TODO: there is a synchronization issue here that needs to be handled
    // before RPC calls are implemented
    this.store.updateLatestFinalizedBlock(this.finalizedBlockRoot, this.finalizedEpoch);
    this.store.updateBestBlock(
        this.headBlock.hash_tree_root(), UnsignedLong.valueOf(this.headBlock.getSlot()));

    // Send event that headState has been updated
    this.eventBus.post(
        new HeadStateEvent(
            BeaconStateWithCache.deepCopy((BeaconStateWithCache) headState), headBlock));
    recordData(date);
  }

  protected Boolean inspectBlock(Optional<BeaconBlock> block) {
    // TODO: check if the fork_head's parent slot is further back than the weak subjectivity
    // period, should we check?
    if (!block.isPresent()) {
      return false;
    }
    if (!this.store.getParent(block.get()).isPresent()) {
      return false;
    }
    UnsignedLong blockTime =
        UnsignedLong.valueOf(block.get().getSlot() * Constants.SECONDS_PER_SLOT);
    // TODO: Here we reject block because time is not there,
    // however, the block is already removed from queue, so
    // we're losing a valid block here.
    if (this.nodeTime.compareTo(blockTime) < 0) {
      LOG.log(Level.FATAL, "We lost a valid block!");
      return false;
    }
    return true;
  }

  protected void processBlock(Optional<BeaconBlock> unprocessedBlock) {
    try {
      Boolean shouldProcessBlock = inspectBlock(unprocessedBlock);
      if (shouldProcessBlock) {

        // Get block, block root and block state root
        BeaconBlock block = unprocessedBlock.get();
        Bytes32 blockRoot = block.signed_root("signature");
        Bytes32 blockStateRoot = block.getState_root();

        // Get parent block, parent block root, parent block state root, and parent block state
        BeaconBlock parentBlock = this.store.getParent(block).get();
        Bytes32 parentBlockStateRoot = parentBlock.getState_root();
        BeaconState parentBlockState = this.store.getState(parentBlockStateRoot).get();

        // Run state transition with no blocks from the parentBlockState.slot to block.slot - 1
        boolean firstLoop = true;
        BeaconStateWithCache currentState =
            BeaconStateWithCache.deepCopy((BeaconStateWithCache) parentBlockState);
        while (currentState.getSlot().compareTo(UnsignedLong.valueOf(block.getSlot() - 1)) < 0) {
          if (firstLoop) {
            STDOUT.log(
                Level.INFO,
                "Running state transition with no blocks from parent block slot: "
                    + currentState.getSlot()
                    + " to slot: "
                    + (block.getSlot() - 1));
            firstLoop = false;
          }
          stateTransition.initiate(currentState, null);
        }

        // Run state transition with the block
        STDOUT.log(Level.INFO, ANSI_PURPLE + "Running state transition with block." + ANSI_RESET);
        stateTransition.initiate(currentState, block);

        Bytes32 newStateRoot = currentState.hash_tree_root();

        // Verify that the state root we have computed is the state root that block is
        // claiming us we should reach, save the block and the state if its correct.
        if (blockStateRoot.equals(newStateRoot)) {
          STDOUT.log(
              Level.INFO,
              ANSI_PURPLE + "Block state root matches the calculated state root." + ANSI_RESET);
          this.store.addProcessedBlock(blockRoot, block);
          this.store.addState(newStateRoot, currentState);
          block
              .getBody()
              .getAttestations()
              .forEach(attestation -> this.store.addProcessedAttestation(attestation));
        } else {
          STDOUT.log(
              Level.INFO,
              ANSI_RED + "Block state root does NOT match the calculated state root!" + ANSI_RESET);
          STDOUT.log(
              Level.INFO,
              ANSI_RED + "Block state root: " + blockStateRoot.toHexString() + ANSI_RESET);
          STDOUT.log(
              Level.INFO, ANSI_RED + "New state root: " + newStateRoot.toHexString() + ANSI_RESET);
        }
      } else {
        STDOUT.log(Level.INFO, "Skipped processing block");
      }
    } catch (NoSuchElementException | IllegalArgumentException | StateTransitionException e) {
      LOG.log(Level.WARN, e.toString());
    }
  }

  protected void updateHeadBlockUsingLMDGhost() {
    // Update justified block and state roots
    updateJustifiedAndFinalized();

    try {
      // Obtain latest justified block and state that will be passed into lmd_ghost
      BeaconState justifiedState = store.getState(justifiedStateRoot).get();
      BeaconBlock justifiedBlock = store.getProcessedBlock(currentJustifiedBlockRoot).get();

      // Run lmd_ghost to get the head block
      this.headBlock = LmdGhost.lmd_ghost(store, justifiedState, justifiedBlock);
    } catch (NoSuchElementException e) {
      LOG.log(Level.FATAL, "Can't update head block using LMDGhost");
    }
  }

  protected void updateJustifiedAndFinalized() {
    // If it is the genesis epoch, keep the justified state root as genesis state root
    // because get_block_root gives an error if the slot is not less than state.slot
    if (BeaconStateUtil.slot_to_epoch(nodeSlot)
            .compareTo(UnsignedLong.valueOf(Constants.GENESIS_EPOCH))
        != 0) {
      try {
        BeaconState headState = store.getState(headBlock.getState_root()).get();
        this.finalizedBlockRoot =
            BeaconStateUtil.get_block_root(
                headState, BeaconStateUtil.get_epoch_start_slot(headState.getFinalized_epoch()));
        this.finalizedEpoch = headState.getFinalized_epoch();
        this.currentJustifiedBlockRoot =
            BeaconStateUtil.get_block_root(
                headState,
                BeaconStateUtil.get_epoch_start_slot(headState.getCurrent_justified_epoch()));

        this.justifiedStateRoot =
            store.getProcessedBlock(currentJustifiedBlockRoot).get().getState_root();
        this.finalizedStateRoot = store.getProcessedBlock(finalizedBlockRoot).get().getState_root();
      } catch (Exception e) {
        LOG.log(Level.FATAL, "Can't update justified and finalized block roots");
      }
    }
  }

  protected void recordData(Date date) {
    BeaconState justifiedState = store.getState(justifiedStateRoot).get();
    BeaconBlock justifiedBlock = store.getProcessedBlock(currentJustifiedBlockRoot).get();
    BeaconState finalizedState = store.getState(finalizedStateRoot).get();
    BeaconBlock finalizedBlock = store.getProcessedBlock(finalizedBlockRoot).get();
    RawRecord record =
        new RawRecord(
            this.nodeSlot.longValue() - Constants.GENESIS_SLOT,
            headState,
            headBlock,
            justifiedState,
            justifiedBlock,
            finalizedState,
            finalizedBlock,
            date);
    this.eventBus.post(record);
  }
}

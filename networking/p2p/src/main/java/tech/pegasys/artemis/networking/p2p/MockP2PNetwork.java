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

package tech.pegasys.artemis.networking.p2p;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.ProposalSignedData;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.pow.api.Eth2GenesisEvent;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public class MockP2PNetwork implements P2PNetwork {

  private final EventBus eventBus;
  private static final ALogger LOG = new ALogger(MockP2PNetwork.class.getName());
  private boolean printDuringDemo = false;

  public MockP2PNetwork(EventBus eventBus) {
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  public MockP2PNetwork(EventBus eventBus, boolean printDuringDemo) {
    this.eventBus = eventBus;
    this.eventBus.register(this);
    this.printDuringDemo = printDuringDemo;
  }

  /**
   * Returns a snapshot of the currently connected peer connections.
   *
   * @return Peers currently connected.
   */
  @Override
  public Collection<?> getPeers() {
    return null;
  }

  /**
   * Connects to a {@link String Peer}.
   *
   * @param peer Peer to connect to.
   * @return Future of the established {}
   */
  @Override
  public CompletableFuture<?> connect(String peer) {
    return null;
  }

  /**
   * Subscribe a to all incoming events.
   *
   * @param event to subscribe to.
   */
  @Override
  public void subscribe(String event) {}

  /** Stops the P2P network layer. */
  @Override
  public void stop() {}

  /**
   * Checks if the node is listening for network connections
   *
   * @return true if the node is listening for network connections, false, otherwise.
   */
  @Override
  public boolean isListening() {
    return false;
  }

  @Override
  public void run() {}

  @Override
  public void close() {
    this.stop();
  }

  @Subscribe
  public void onEth2GenesisEvent(Eth2GenesisEvent event) {
    simulateNewMessages();
  }

  private BLSSignature setEpochSignature(BeaconState state) {
    /**
     * epoch_signature = bls_sign( privkey=validator.privkey, # privkey store locally, not in state
     * message_hash=int_to_bytes32(slot_to_epoch(block.slot)), domain=get_domain( fork=fork, #
     * `fork` is the fork object at the slot `block.slot` epoch=slot_to_epoch(block.slot),
     * domain_type=DOMAIN_RANDAO, ))
     */

    // State hasn't been updated(transition initiated) when its passed to setEpochSignature,
    // and thus the slot is one less than what it should be for the new block, that is why we
    // increment it here.
    UnsignedLong slot = state.getSlot().plus(UnsignedLong.ONE);
    UnsignedLong epoch = BeaconStateUtil.slot_to_epoch(state.getSlot());
    // BLSKeyPair keypair = BLSKeyPair.random(Math.toIntExact(Constants.GENESIS_SLOT) + i);

    int proposerIndex = BeaconStateUtil.get_beacon_proposer_index(state, slot);
    Validator proposer = state.getValidator_registry().get(proposerIndex);
    int genesisSlot = Math.toIntExact(Constants.GENESIS_SLOT);
    BLSKeyPair keypair = BLSKeyPair.random();
    // TODO: O(n), but in reality we will have the keypair in the validator
    for (int i = 0; i < 128; i++) {
      keypair = BLSKeyPair.random(genesisSlot + i);
      if (keypair.getPublicKey().equals(proposer.getPubkey())) {
        break;
      }
    }

    UnsignedLong domain =
        BeaconStateUtil.get_domain(state.getFork(), epoch, Constants.DOMAIN_RANDAO);
    Bytes32 currentEpochBytes = Bytes32.leftPad(Bytes.ofUnsignedLong(epoch.longValue()));
    LOG.log(Level.INFO, "Sign Epoch", printDuringDemo);
    LOG.log(Level.INFO, "Proposer pubkey: " + keypair.getPublicKey(), printDuringDemo);
    LOG.log(Level.INFO, "state: " + HashTreeUtil.hash_tree_root(state.toBytes()), printDuringDemo);
    LOG.log(Level.INFO, "slot: " + slot, printDuringDemo);
    LOG.log(Level.INFO, "domain: " + domain, printDuringDemo);
    return BLSSignature.sign(keypair, currentEpochBytes, domain.longValue());
  }

  private BLSSignature signProposalData(BeaconState state, BeaconBlock block) {
    // Let block_without_signature_root be the hash_tree_root of block where
    //   block.signature is set to EMPTY_SIGNATURE.
    block.setSignature(Constants.EMPTY_SIGNATURE);
    Bytes32 blockWithoutSignatureRootHash = HashTreeUtil.hash_tree_root(block.toBytes());

    // Let proposal_root = hash_tree_root(ProposalSignedData(state.slot,
    //   BEACON_CHAIN_SHARD_NUMBER, block_without_signature_root)).
    ProposalSignedData proposalSignedData =
        new ProposalSignedData(
            state.getSlot(), Constants.BEACON_CHAIN_SHARD_NUMBER, blockWithoutSignatureRootHash);
    Bytes proposalRoot = HashTreeUtil.hash_tree_root(proposalSignedData.toBytes());

    // Verify that bls_verify(pubkey=state.validator_registry[get_beacon_proposer_index(state,
    //   state.slot)].pubkey, message=proposal_root, signature=block.signature,
    //   domain=get_domain(state.fork, state.slot, DOMAIN_PROPOSAL)) is valid.
    //    BLSKeyPair keypair = BLSKeyPair.random(Math.toIntExact(Constants.GENESIS_SLOT) + i);

    int proposerIndex = BeaconStateUtil.get_beacon_proposer_index(state, state.getSlot());
    Validator proposer = state.getValidator_registry().get(proposerIndex);
    int slot = Math.toIntExact(Constants.GENESIS_SLOT);
    BLSKeyPair keypair = BLSKeyPair.random();
    // TODO: O(n), but in reality we will have the keypair in the validator
    for (int i = 0; i < 128; i++) {
      keypair = BLSKeyPair.random(slot + i);
      if (keypair.getPublicKey().equals(proposer.getPubkey())) {
        break;
      }
    }

    UnsignedLong domain =
        BeaconStateUtil.get_domain(
            state.getFork(),
            BeaconStateUtil.slot_to_epoch(state.getSlot()),
            Constants.DOMAIN_PROPOSAL);
    BLSSignature signature = BLSSignature.sign(keypair, proposalRoot, domain.longValue());
    LOG.log(Level.INFO, "Sign Proposal", printDuringDemo);
    LOG.log(Level.INFO, "Proposer pubkey: " + keypair.getPublicKey(), printDuringDemo);
    LOG.log(Level.INFO, "state: " + HashTreeUtil.hash_tree_root(state.toBytes()), printDuringDemo);
    LOG.log(Level.INFO, "proposal root: " + proposalRoot.toHexString(), printDuringDemo);
    LOG.log(Level.INFO, "block signature: " + signature.toString(), printDuringDemo);
    LOG.log(Level.INFO, "slot: " + state.getSlot().longValue(), printDuringDemo);
    LOG.log(Level.INFO, "domain: " + domain, printDuringDemo);
    return signature;
  }

  private void simulateNewMessages() {
    try {
      StateTransition stateTransition = new StateTransition();

      BeaconState state = DataStructureUtil.createInitialBeaconState();
      Bytes32 state_root = HashTreeUtil.hash_tree_root(state.toBytes());
      BeaconBlock block = BeaconBlock.createGenesis(state_root);
      Bytes32 parent_root = HashTreeUtil.hash_tree_root(block.toBytes());

      ArrayList<Deposit> deposits = new ArrayList<>();
      while (true) {
        LOG.log(Level.INFO, "In MockP2PNetwork", printDuringDemo);
        state = BeaconState.deepCopy(state);
        state_root = Bytes32.ZERO;
        block =
            DataStructureUtil.newBeaconBlock(
                state.getSlot().plus(UnsignedLong.ONE), parent_root, state_root, deposits);

        BLSSignature epoch_signature = setEpochSignature(state);
        block.setRandao_reveal(epoch_signature);
        stateTransition.initiate(state, block);
        state_root = HashTreeUtil.hash_tree_root(state.toBytes());
        block.setState_root(state_root);
        BLSSignature signed_proposal = signProposalData(state, block);
        block.setSignature(signed_proposal);

        LOG.log(Level.INFO, "MockP2PNetwork - NEWLY PRODUCED BLOCK", printDuringDemo);
        LOG.log(Level.INFO, "MockP2PNetwork - block.slot: " + block.getSlot(), printDuringDemo);
        LOG.log(
            Level.INFO,
            "MockP2PNetwork - block.parent_root: " + block.getParent_root(),
            printDuringDemo);
        LOG.log(
            Level.INFO,
            "MockP2PNetwork - block.state_root: " + block.getState_root(),
            printDuringDemo);
        parent_root = HashTreeUtil.hash_tree_root(block.toBytes());
        LOG.log(Level.INFO, "MockP2PNetwork - block.block_root: " + parent_root, printDuringDemo);

        this.eventBus.post(block);
        LOG.log(Level.INFO, "End MockP2PNetwork", printDuringDemo);
        Thread.sleep(6000);
      }
    } catch (InterruptedException | StateTransitionException e) {
      LOG.log(Level.WARN, e.toString(), printDuringDemo);
    }
  }
}

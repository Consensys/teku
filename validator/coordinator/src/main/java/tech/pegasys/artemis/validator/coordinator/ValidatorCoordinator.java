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

package tech.pegasys.artemis.validator.coordinator;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.SECP256K1.PublicKey;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.Proposal;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.pow.api.Eth2GenesisEvent;
import tech.pegasys.artemis.services.ServiceConfig;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

/** This class coordinates the activity between the validator clients and the the beacon chain */
public class ValidatorCoordinator {
  private static final ALogger LOG = new ALogger(ValidatorCoordinator.class.getName());
  private final EventBus eventBus;

  private StateTransition stateTransition;
  private BeaconState state;
  private BeaconBlock block;
  private Bytes32 blockRoot;
  private Bytes32 stateRoot;
  private ArrayList<Deposit> deposits;
  private final Bytes32 MockStateRoot = Bytes32.ZERO;
  private final Boolean printEnabled = false;
  private PublicKey nodeIdentity;
  private int numValidators;
  private int numNodes;

  public ValidatorCoordinator(ServiceConfig config) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    this.nodeIdentity = config.getKeyPair().publicKey();
    this.numValidators = config.getConfig().getNumValidators();
    this.numNodes = config.getConfig().getNumNodes();
  }

  @Subscribe
  public void onEth2GenesisEvent(Eth2GenesisEvent event) {
    initializeValidators();
  }

  @Subscribe
  public void onNewSlot(Date date) {
    simulateNewMessages();
  }

  private void initializeValidators() {
    stateTransition = new StateTransition(false);
    state = DataStructureUtil.createInitialBeaconState(numValidators);
    stateRoot = HashTreeUtil.hash_tree_root(state.toBytes());
    block = BeaconBlock.createGenesis(stateRoot);
    blockRoot = HashTreeUtil.hash_tree_root(block.toBytes());
    deposits = new ArrayList<>();
  }

  private void simulateNewMessages() {
    List<Attestation> current_attestations;
    try {
      LOG.log(Level.INFO, "In MockP2PNetwork", printEnabled);
      if (state
              .getSlot()
              .compareTo(
                  UnsignedLong.valueOf(
                      Constants.GENESIS_SLOT + Constants.MIN_ATTESTATION_INCLUSION_DELAY))
          > 0) {
        LOG.log(Level.INFO, "Here comes an attestation", printEnabled);
        current_attestations =
            DataStructureUtil.createAttestations(
                state,
                state
                    .getSlot()
                    .minus(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY)),
                numValidators);
        block =
            DataStructureUtil.newBeaconBlock(
                state.getSlot().plus(UnsignedLong.ONE),
                blockRoot,
                MockStateRoot,
                deposits,
                current_attestations);
      } else {
        block =
            DataStructureUtil.newBeaconBlock(
                state.getSlot().plus(UnsignedLong.ONE),
                blockRoot,
                MockStateRoot,
                deposits,
                new ArrayList<>());
      }
      BLSKeyPair keypair = getProposerKeyPair(state);
      BLSSignature epoch_signature = setEpochSignature(state, keypair);
      block.setRandao_reveal(epoch_signature);
      stateTransition.initiate(state, block, blockRoot);
      stateRoot = HashTreeUtil.hash_tree_root(state.toBytes());
      block.setState_root(stateRoot);
      BLSSignature signed_proposal = signProposalData(state, block, keypair);
      block.setSignature(signed_proposal);
      blockRoot = HashTreeUtil.hash_tree_root(block.toBytes());

      LOG.log(Level.INFO, "MockP2PNetwork - NEWLY PRODUCED BLOCK", printEnabled);
      LOG.log(Level.INFO, "MockP2PNetwork - block.slot: " + block.getSlot(), printEnabled);
      LOG.log(
          Level.INFO,
          "MockP2PNetwork - block.parent_root: " + block.getParent_root(),
          printEnabled);
      LOG.log(
          Level.INFO, "MockP2PNetwork - block.state_root: " + block.getState_root(), printEnabled);
      LOG.log(Level.INFO, "MockP2PNetwork - block.block_root: " + blockRoot, printEnabled);

      this.eventBus.post(block);
      LOG.log(Level.INFO, "End MockP2PNetwork", printEnabled);
    } catch (StateTransitionException e) {
      LOG.log(Level.WARN, e.toString(), printEnabled);
    }
  }

  private BLSKeyPair getProposerKeyPair(BeaconState state) {
    // State hasn't been updated(transition initiated) when its passed to setEpochSignature,
    // and thus the slot is one less than what it should be for the new block, that is why we
    // increment it here.
    UnsignedLong slot = state.getSlot().plus(UnsignedLong.ONE);
    // BLSKeyPair keypair = BLSKeyPair.random(Math.toIntExact(Constants.GENESIS_SLOT) + i);

    int proposerIndex = BeaconStateUtil.get_beacon_proposer_index(state, slot);
    Validator proposer = state.getValidator_registry().get(proposerIndex);
    BLSKeyPair keypair = BLSKeyPair.random();
    // TODO: O(n), but in reality we will have the keypair in the validator
    for (int i = 0; i < numValidators; i++) {
      keypair = BLSKeyPair.random(i);
      if (keypair.getPublicKey().equals(proposer.getPubkey())) {
        break;
      }
    }
    return keypair;
  }

  private BLSSignature setEpochSignature(BeaconState state, BLSKeyPair keypair) {
    /**
     * epoch_signature = bls_sign( privkey=validator.privkey, # privkey store locally, not in state
     * message_hash=int_to_bytes32(slot_to_epoch(block.slot)), domain=get_domain( fork=fork, #
     * `fork` is the fork object at the slot `block.slot` epoch=slot_to_epoch(block.slot),
     * domain_type=DOMAIN_RANDAO, ))
     */
    UnsignedLong slot = state.getSlot().plus(UnsignedLong.ONE);
    UnsignedLong epoch = BeaconStateUtil.slot_to_epoch(slot);

    UnsignedLong domain =
        BeaconStateUtil.get_domain(state.getFork(), epoch, Constants.DOMAIN_RANDAO);
    Bytes32 messageHash =
        HashTreeUtil.hash_tree_root(BeaconStateUtil.int_to_bytes(epoch.longValue(), 8));
    LOG.log(Level.INFO, "Sign Epoch", printEnabled);
    LOG.log(Level.INFO, "Proposer pubkey: " + keypair.getPublicKey(), printEnabled);
    LOG.log(Level.INFO, "state: " + HashTreeUtil.hash_tree_root(state.toBytes()), printEnabled);
    LOG.log(Level.INFO, "slot: " + slot, printEnabled);
    LOG.log(Level.INFO, "domain: " + domain, printEnabled);
    return BLSSignature.sign(keypair, messageHash, domain.longValue());
  }

  private BLSSignature signProposalData(BeaconState state, BeaconBlock block, BLSKeyPair keypair) {
    // Let proposal = Proposal(block.slot, BEACON_CHAIN_SHARD_NUMBER,
    //   signed_root(block, "signature"), block.signature).
    Proposal proposal =
        new Proposal(
            UnsignedLong.fromLongBits(block.getSlot()),
            Constants.BEACON_CHAIN_SHARD_NUMBER,
            block.signedRoot("signature"),
            block.getSignature());
    Bytes32 proposalRoot = proposal.signedRoot("signature");

    UnsignedLong domain =
        BeaconStateUtil.get_domain(
            state.getFork(),
            BeaconStateUtil.slot_to_epoch(state.getSlot()),
            Constants.DOMAIN_PROPOSAL);
    BLSSignature signature = BLSSignature.sign(keypair, proposalRoot, domain.longValue());
    LOG.log(Level.INFO, "Sign Proposal", printEnabled);
    LOG.log(Level.INFO, "Proposer pubkey: " + keypair.getPublicKey(), printEnabled);
    LOG.log(Level.INFO, "state: " + HashTreeUtil.hash_tree_root(state.toBytes()), printEnabled);
    LOG.log(Level.INFO, "proposal root: " + proposalRoot.toHexString(), printEnabled);
    LOG.log(Level.INFO, "block signature: " + signature.toString(), printEnabled);
    LOG.log(Level.INFO, "slot: " + state.getSlot().longValue(), printEnabled);
    LOG.log(Level.INFO, "domain: " + domain, printEnabled);
    return signature;
  }
}

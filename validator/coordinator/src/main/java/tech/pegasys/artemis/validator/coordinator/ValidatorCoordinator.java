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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.SECP256K1;
import net.consensys.cava.units.bigints.UInt256;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.Proposal;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.services.ServiceConfig;
import tech.pegasys.artemis.statetransition.HeadStateEvent;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

/** This class coordinates the activity between the validator clients and the beacon chain */
public class ValidatorCoordinator {
  private static final ALogger LOG = new ALogger(ValidatorCoordinator.class.getName());
  private final EventBus eventBus;

  private StateTransition stateTransition;
  private final Boolean printEnabled = false;
  private SECP256K1.SecretKey nodeIdentity;
  private int numValidators;
  private int numNodes;
  private BeaconBlock validatorBlock;
  private ArrayList<Deposit> newDeposits = new ArrayList<>();
  private final HashMap<BLSPublicKey, BLSKeyPair> validatorSet = new HashMap<>();
  static final Integer UNPROCESSED_BLOCKS_LENGTH = 100;
  private final PriorityBlockingQueue<Attestation> attestationsQueue =
      new PriorityBlockingQueue<Attestation>(
          UNPROCESSED_BLOCKS_LENGTH, Comparator.comparing(Attestation::getSlot));

  public ValidatorCoordinator(ServiceConfig config) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    this.nodeIdentity =
        SECP256K1.SecretKey.fromBytes(Bytes32.fromHexString(config.getConfig().getIdentity()));
    this.numValidators = config.getConfig().getNumValidators();
    this.numNodes = config.getConfig().getNumNodes();

    initializeValidators();

    stateTransition = new StateTransition(printEnabled);
    BeaconStateWithCache initialBeaconState =
        DataStructureUtil.createInitialBeaconState(numValidators);
    Bytes32 initialStateRoot = HashTreeUtil.hash_tree_root(initialBeaconState.toBytes());
    BeaconBlock genesisBlock = BeaconBlock.createGenesis(initialStateRoot);

    createBlockIfNecessary(initialBeaconState, genesisBlock);
  }

  @Subscribe
  public void onNewSlot(Date date) {
    if (validatorBlock != null) {
      this.eventBus.post(validatorBlock);
      validatorBlock = null;
    }
  }

  @Subscribe
  public void onNewHeadStateEvent(HeadStateEvent headStateEvent) {
    // Retrieve headState and headBlock from event
    BeaconStateWithCache headState = headStateEvent.getHeadState();
    BeaconBlock headBlock = headStateEvent.getHeadBlock();

    List<Attestation> attestations =
        AttestationUtil.createAttestations(headState, headBlock, validatorSet);

    for (Attestation attestation : attestations) {
      this.eventBus.post(attestation);
    }

    List<Attestation> blockAttestations = headBlock.getBody().getAttestations();
    synchronized (this.attestationsQueue) {
      for (Attestation blockAttestation : blockAttestations) {
        attestationsQueue.removeIf(
            attestation -> {
              return attestation.equals(blockAttestation);
            });
      }
    }
    // Copy state so that state transition during block creation does not manipulate headState in
    // storage
    BeaconStateWithCache newHeadState = BeaconStateWithCache.deepCopy(headState);
    createBlockIfNecessary(newHeadState, headBlock);
  }

  @Subscribe
  public void onNewAttestation(Attestation attestation) {
    synchronized (this.attestationsQueue) {
      // Store attestations in a priority queue
      if (!attestationsQueue.contains(attestation)) {
        attestationsQueue.add(attestation);
      }
    }
  }

  private void initializeValidators() {
    // Add all validators to validatorSet hashMap
    int nodeCounter = UInt256.fromBytes(nodeIdentity.bytes()).mod(numNodes).intValue();
    // LOG.log(Level.DEBUG, "nodeCounter: " + nodeCounter);
    // if (nodeCounter == 0) {

    int startIndex = nodeCounter * (numValidators / numNodes);
    int endIndex =
        startIndex
            + (numValidators / numNodes - 1)
            + (int) Math.floor(nodeCounter / Math.max(1, numNodes - 1));
    endIndex = Math.min(endIndex, numValidators - 1);
    // int startIndex = 0;
    // int endIndex = numValidators-1;
    LOG.log(Level.DEBUG, "startIndex: " + startIndex + " endIndex: " + endIndex);
    for (int i = startIndex; i <= endIndex; i++) {
      BLSKeyPair keypair = BLSKeyPair.random(i);
      LOG.log(Level.DEBUG, "i = " + i + ": " + keypair.getPublicKey().toString());
      validatorSet.put(keypair.getPublicKey(), keypair);
    }
    // }
  }

  private void createBlockIfNecessary(BeaconStateWithCache headState, BeaconBlock headBlock) {
    // Calculate the block proposer index, and if we have the
    // block proposer in our set of validators, produce the block
    Integer proposerIndex =
        BeaconStateUtil.get_beacon_proposer_index(
            headState, headState.getSlot().plus(UnsignedLong.ONE));
    BLSPublicKey proposerPubkey = headState.getValidator_registry().get(proposerIndex).getPubkey();
    if (validatorSet.containsKey(proposerPubkey)) {
      Bytes32 blockRoot = HashTreeUtil.hash_tree_root(headBlock.toBytes());
      createNewBlock(headState, blockRoot, validatorSet.get(proposerPubkey));
    }
  }

  private void createNewBlock(
      BeaconStateWithCache headState, Bytes32 blockRoot, BLSKeyPair keypair) {
    try {
      List<Attestation> current_attestations;
      final Bytes32 MockStateRoot = Bytes32.ZERO;
      BeaconBlock block;
      if (headState
              .getSlot()
              .compareTo(
                  UnsignedLong.valueOf(
                      Constants.GENESIS_SLOT + Constants.MIN_ATTESTATION_INCLUSION_DELAY))
          > 0) {
        UnsignedLong attestation_slot =
            headState
                .getSlot()
                .minus(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY));
        current_attestations =
            AttestationUtil.getAttestationsUntilSlot(attestationsQueue, attestation_slot);
        block =
            DataStructureUtil.newBeaconBlock(
                headState.getSlot().plus(UnsignedLong.ONE),
                blockRoot,
                MockStateRoot,
                newDeposits,
                current_attestations);
      } else {
        block =
            DataStructureUtil.newBeaconBlock(
                headState.getSlot().plus(UnsignedLong.ONE),
                blockRoot,
                MockStateRoot,
                newDeposits,
                new ArrayList<>());
      }

      BLSSignature epoch_signature = setEpochSignature(headState, keypair);
      block.setRandao_reveal(epoch_signature);
      stateTransition.initiate(headState, block, blockRoot);
      Bytes32 stateRoot = HashTreeUtil.hash_tree_root(headState.toBytes());
      block.setState_root(stateRoot);
      BLSSignature signed_proposal = signProposalData(headState, block, keypair);
      block.setSignature(signed_proposal);
      validatorBlock = block;

      LOG.log(Level.INFO, "ValidatorCoordinator - NEWLY PRODUCED BLOCK", printEnabled);
      LOG.log(Level.INFO, "ValidatorCoordinator - block.slot: " + block.getSlot(), printEnabled);
      LOG.log(
          Level.INFO,
          "ValidatorCoordinator - block.parent_root: " + block.getParent_root(),
          printEnabled);
      LOG.log(
          Level.INFO,
          "ValidatorCoordinator - block.state_root: " + block.getState_root(),
          printEnabled);

      LOG.log(Level.INFO, "End ValidatorCoordinator", printEnabled);
    } catch (StateTransitionException e) {
      LOG.log(Level.WARN, e.toString(), printEnabled);
    }
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

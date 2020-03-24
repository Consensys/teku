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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;

import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.validator.MessageSignerService;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;

public class BlockProposalUtil {

  private static final Logger LOG = LogManager.getLogger();

  private final StateTransition stateTransition;

  public BlockProposalUtil(final StateTransition stateTransition) {
    this.stateTransition = stateTransition;
  }

  public BeaconBlock createNewUnsignedBlock(
      final UnsignedLong newSlot,
      final BLSSignature randaoReveal,
      final BeaconState state,
      final Bytes32 parentBlockSigningRoot,
      final Eth1Data eth1Data,
      final SSZList<Attestation> attestations,
      final SSZList<ProposerSlashing> slashings,
      final SSZList<Deposit> deposits)
      throws StateTransitionException {
    // Create block body
    BeaconBlockBody beaconBlockBody = new BeaconBlockBody();
    beaconBlockBody.setEth1_data(eth1Data);
    beaconBlockBody.setDeposits(deposits);
    beaconBlockBody.setAttestations(attestations);
    beaconBlockBody.setProposer_slashings(slashings);
    beaconBlockBody.setRandao_reveal(randaoReveal);

    // Create initial block with some stubs
    final Bytes32 tmpStateRoot = Bytes32.ZERO;
    BeaconBlock newBlock =
        new BeaconBlock(newSlot, parentBlockSigningRoot, tmpStateRoot, beaconBlockBody);

    // Run state transition and set state root
    Bytes32 stateRoot =
        stateTransition
            .initiate(state, new SignedBeaconBlock(newBlock, BLSSignature.empty()), false)
            .hash_tree_root();
    newBlock.setState_root(stateRoot);

    return newBlock;
  }

  public BeaconBlock createNewUnsignedBlock(
      final MessageSignerService signer,
      final UnsignedLong newSlot,
      final BeaconState state,
      final Bytes32 parentBlockSigningRoot,
      final Eth1Data eth1Data,
      final SSZList<Attestation> attestations,
      final SSZList<ProposerSlashing> slashings,
      final SSZList<Deposit> deposits)
      throws StateTransitionException {
    final UnsignedLong newEpoch = compute_epoch_at_slot(newSlot);

    // Create block body
    BeaconBlockBody beaconBlockBody = new BeaconBlockBody();
    beaconBlockBody.setEth1_data(eth1Data);
    beaconBlockBody.setDeposits(deposits);
    beaconBlockBody.setAttestations(attestations);
    beaconBlockBody.setProposer_slashings(slashings);
    beaconBlockBody.setRandao_reveal(get_epoch_signature(state, newEpoch, signer));

    // Create initial block with some stubs
    final Bytes32 tmpStateRoot = Bytes32.ZERO;
    BeaconBlock newBlock =
        new BeaconBlock(newSlot, parentBlockSigningRoot, tmpStateRoot, beaconBlockBody);

    // Run state transition and set state root
    Bytes32 stateRoot =
        stateTransition
            .initiate(state, new SignedBeaconBlock(newBlock, BLSSignature.empty()), false)
            .hash_tree_root();
    newBlock.setState_root(stateRoot);

    return newBlock;
  }

  public SignedBeaconBlock createNewBlock(
      final MessageSignerService signer,
      final UnsignedLong newSlot,
      final BeaconState state,
      final Bytes32 parentBlockSigningRoot,
      final Eth1Data eth1Data,
      final SSZList<Attestation> attestations,
      final SSZList<ProposerSlashing> slashings,
      final SSZList<Deposit> deposits)
      throws StateTransitionException {
    final BeaconBlock newBlock =
        createNewUnsignedBlock(
            signer,
            newSlot,
            state,
            parentBlockSigningRoot,
            eth1Data,
            attestations,
            slashings,
            deposits);

    // Sign block and set block signature
    BLSSignature blockSignature = get_block_signature(state, newBlock, signer);

    return new SignedBeaconBlock(newBlock, blockSignature);
  }

  public SignedBeaconBlock createEmptyBlock(
      final MessageSignerService signer,
      final UnsignedLong newSlot,
      final BeaconState previousState,
      final Bytes32 parentBlockRoot)
      throws StateTransitionException {
    final UnsignedLong newEpoch = compute_epoch_at_slot(newSlot);
    return createNewBlock(
        signer,
        newSlot,
        previousState,
        parentBlockRoot,
        StartupUtil.get_eth1_data_stub(previousState, newEpoch),
        BeaconBlockBodyLists.createAttestations(),
        BeaconBlockBodyLists.createProposerSlashings(),
        BeaconBlockBodyLists.createDeposits());
  }

  public SignedBeaconBlock createBlockWithAttestations(
      final MessageSignerService signer,
      final UnsignedLong newSlot,
      final BeaconState previousState,
      final Bytes32 parentBlockSigningRoot,
      final SSZList<Attestation> attestations)
      throws StateTransitionException {
    final UnsignedLong newEpoch = compute_epoch_at_slot(newSlot);
    return createNewBlock(
        signer,
        newSlot,
        previousState,
        parentBlockSigningRoot,
        StartupUtil.get_eth1_data_stub(previousState, newEpoch),
        attestations,
        BeaconBlockBodyLists.createProposerSlashings(),
        BeaconBlockBodyLists.createDeposits());
  }

  public BLSPublicKey getProposerForSlot(final BeaconState preState, final UnsignedLong slot) {
    int proposerIndex = getProposerIndexForSlot(preState, slot);
    return preState.getValidators().get(proposerIndex).getPubkey();
  }

  public int getProposerIndexForSlot(final BeaconState preState, final UnsignedLong slot) {
    MutableBeaconState state = preState.createWritableCopy();
    try {
      stateTransition.process_slots(state, slot);
    } catch (SlotProcessingException | EpochProcessingException e) {
      LOG.fatal("Coordinator checking proposer index exception", e);
    }
    return BeaconStateUtil.get_beacon_proposer_index(state);
  }

  /**
   * Gets the block signature from the Validator Client using gRPC
   *
   * @param state The post-state associated with the block
   * @param block The block to sign
   * @param signer A utility for generating the signature given the domain and message to sign
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/validator/0_beacon-chain-validator.md#signature</a>
   */
  private BLSSignature get_block_signature(
      final BeaconState state, final BeaconBlock block, final MessageSignerService signer) {
    final Bytes domain =
        get_domain(state, Constants.DOMAIN_BEACON_PROPOSER, compute_epoch_at_slot(block.getSlot()));
    final Bytes signing_root = compute_signing_root(block, domain);
    return signer.signBlock(signing_root).join();
  }

  /**
   * Gets the epoch signature used for RANDAO from the Validator Client using gRPC
   *
   * @param state
   * @param epoch
   * @param signer
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/validator/0_beacon-chain-validator.md#randao-reveal</a>
   */
  public BLSSignature get_epoch_signature(
      final BeaconState state, final UnsignedLong epoch, final MessageSignerService signer) {
    Bytes domain = get_domain(state, Constants.DOMAIN_RANDAO, epoch);
    Bytes signing_root = compute_signing_root(epoch.longValue(), domain);
    return signer.signRandaoReveal(signing_root).join();
  }
}

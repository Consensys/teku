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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.validator.Signer;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public class BlockCreator {
  private final StateTransition stateTransition;

  public BlockCreator(final StateTransition stateTransition) {
    this.stateTransition = stateTransition;
  }

  public BeaconBlock createNewBlock(
      final Signer signer,
      final UnsignedLong newSlot,
      final BeaconStateWithCache previousState,
      final Bytes32 parentBlockSigningRoot,
      final SSZList<Attestation> attestations,
      final SSZList<ProposerSlashing> slashings,
      final SSZList<Deposit> deposits)
      throws StateTransitionException {
    final UnsignedLong newEpoch = compute_epoch_at_slot(newSlot);
    final BeaconStateWithCache newState = BeaconStateWithCache.deepCopy(previousState);

    // Create block body
    BeaconBlockBody beaconBlockBody = new BeaconBlockBody();
    beaconBlockBody.setEth1_data(StartupUtil.get_eth1_data_stub(newState, newEpoch));
    beaconBlockBody.setDeposits(deposits);
    beaconBlockBody.setAttestations(attestations);
    beaconBlockBody.setProposer_slashings(slashings);
    // Create initial block with some stubs
    final Bytes32 tmpStateRoot = Bytes32.ZERO;
    final BLSSignature tmpSignature = BLSSignature.empty();
    final BeaconBlock newBlock =
        new BeaconBlock(
            newSlot, parentBlockSigningRoot, tmpStateRoot, beaconBlockBody, tmpSignature);

    // Run state transition and set state root
    Bytes32 stateRoot = stateTransition.initiate(newState, newBlock, false).hash_tree_root();
    newBlock.setState_root(stateRoot);

    // Set randao reveal
    beaconBlockBody.setRandao_reveal(get_epoch_signature(newState, newEpoch, signer));

    // Sign block and set block signature
    BLSSignature blockSignature = getBlockSignature(newState, newBlock, signer);
    newBlock.setSignature(blockSignature);

    return newBlock;
  }

  public BeaconBlock createEmptyBlock(
      final Signer signer,
      final UnsignedLong newSlot,
      final BeaconStateWithCache previousState,
      final Bytes32 parentBlockSigningRoot)
      throws StateTransitionException {
    return createNewBlock(
        signer,
        newSlot,
        previousState,
        parentBlockSigningRoot,
        BeaconBlockBodyLists.createAttestations(),
        BeaconBlockBodyLists.createProposerSlashings(),
        BeaconBlockBodyLists.createDeposits());
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
  private BLSSignature getBlockSignature(
      final BeaconState state, final BeaconBlock block, final Signer signer) {
    final Bytes domain =
        get_domain(
            state,
            Constants.DOMAIN_BEACON_PROPOSER,
            BeaconStateUtil.compute_epoch_at_slot(block.getSlot()));

    final Bytes32 blockRoot = block.signing_root("signature");

    return signer.sign(blockRoot, domain);
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
      final BeaconState state, final UnsignedLong epoch, final Signer signer) {
    Bytes32 messageHash =
        HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(epoch.longValue()));
    Bytes domain = get_domain(state, Constants.DOMAIN_RANDAO, epoch);
    return signer.sign(messageHash, domain);
  }
}

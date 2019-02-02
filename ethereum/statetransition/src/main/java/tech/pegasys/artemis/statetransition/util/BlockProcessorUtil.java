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

package tech.pegasys.artemis.statetransition.util;

import static java.lang.Math.toIntExact;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.crypto.Hash;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.ProposalSignedData;
import tech.pegasys.artemis.statetransition.BeaconState;
import tech.pegasys.artemis.util.bls.BLSVerify;

public class BlockProcessorUtil {

  /**
   * Spec:
   * https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#proposer-signature
   *
   * @param state
   * @param block
   */
  public static boolean verify_signature(BeaconState state, BeaconBlock block) {
    // Let block_without_signature_root be the hash_tree_root of block where
    // block.signature is set
    // to EMPTY_SIGNATURE.
    block.setSignature(Constants.EMPTY_SIGNATURE);
    Bytes32 blockHash = TreeHashUtil.hash_tree_root(block.toBytes());
    // Let proposal_root = hash_tree_root(ProposalSignedData(state.slot,
    // BEACON_CHAIN_SHARD_NUMBER,
    // block_without_signature_root)).
    ProposalSignedData signedData =
        new ProposalSignedData(
            UnsignedLong.valueOf(state.getSlot()), Constants.BEACON_CHAIN_SHARD_NUMBER, blockHash);
    Bytes32 proposalRoot = TreeHashUtil.hash_tree_root(signedData.getBlock_hash());
    // Verify that
    // bls_verify(pubkey=state.validator_registry[get_beacon_proposer_index(state,
    // state.slot)].pubkey, message=proposal_root, signature=block.signature,
    // domain=get_domain(state.fork,
    // state.slot, DOMAIN_PROPOSAL)).
    int proposerIndex =
        BeaconState.get_beacon_proposer_index(state, Math.toIntExact(state.getSlot()));
    Bytes48 pubkey = state.getValidator_registry().get(proposerIndex).getPubkey();
    return BLSVerify.bls_verify(
        pubkey, proposalRoot, block.getSignature(), Constants.DOMAIN_PROPOSAL);
  }

  /**
   * Spec: https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#randao
   *
   * @param state
   * @param block
   */
  public static void verify_and_update_randao(BeaconState state, BeaconBlock block) {
    // Let proposer = state.validator_registry[get_beacon_proposer_index(state, state.slot)].
    int proposerIndex =
        BeaconState.get_beacon_proposer_index(state, Math.toIntExact(state.getSlot()));
    Bytes48 pubkey = state.getValidator_registry().get(proposerIndex).getPubkey();
    long epoch = BeaconStateUtil.get_current_epoch(state);
    Bytes32 epochBytes = Bytes32.wrap(Bytes.minimalBytes(epoch));
    // Verify that bls_verify(pubkey=proposer.pubkey,
    // message=int_to_bytes32(get_current_epoch(state)), signature=block.randao_reveal, domain=
    // get_domain(state.fork, get_current_epoch(state), DOMAIN_RANDAO)).
    BLSVerify.bls_verify(pubkey, epochBytes, block.getRandao_reveal(), Constants.DOMAIN_RANDAO);
    // state.latest_randao_mixes[get_current_epoch(state) % LATEST_RANDAO_MIXES_LENGTH] =
    // xor(get_randao_mix(state, get_current_epoch(state)), hash(block.randao_reveal))
    int index = toIntExact(epoch) % Constants.LATEST_RANDAO_MIXES_LENGTH;
    state.getLatest_randao_mixes().get(index).xor(Hash.keccak256(epochBytes));
  }

  public static void tally_pow_receipt_root_vote(BeaconState state, BeaconBlock block) {}
}

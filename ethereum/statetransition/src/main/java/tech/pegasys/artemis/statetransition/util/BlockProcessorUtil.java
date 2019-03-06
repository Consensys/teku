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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_ATTESTATION;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_EXIT;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_PROPOSAL;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_RANDAO;
import static tech.pegasys.artemis.datastructures.Constants.EMPTY_SIGNATURE;
import static tech.pegasys.artemis.datastructures.Constants.EPOCH_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_ATTESTER_SLASHINGS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSITS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_PROPOSER_SLASHINGS;
import static tech.pegasys.artemis.datastructures.Constants.MIN_ATTESTATION_INCLUSION_DELAY;
import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_attestation_participants;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_bitfield_bit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_block_root;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_crosslink_committees_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_entry_exit_effect_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_epoch_start_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initiate_validator_exit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_double_vote;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_surround_vote;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.penalize_validator;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.process_deposit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.slot_to_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.verify_slashable_attestation;
import static tech.pegasys.artemis.util.bls.BLSAggregate.bls_aggregate_pubkeys;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify_multiple;
import static tech.pegasys.artemis.util.hashtree.HashTreeUtil.hash_tree_root;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataVote;
import tech.pegasys.artemis.datastructures.blocks.ProposalSignedData;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.Exit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SlashableAttestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.util.bls.BLSException;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public class BlockProcessorUtil {

  private static final Logger LOG = LogManager.getLogger(BlockProcessorUtil.class.getName());
  /**
   * Spec: https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#slot-1
   *
   * @param state
   * @param block
   */
  public static boolean verify_slot(BeaconState state, BeaconBlock block) {
    // Verify that block.slot == state.slot
    return state.getSlot().compareTo(UnsignedLong.valueOf(block.getSlot())) == 0;
  }

  /**
   * Spec:
   * https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#proposer-signature
   *
   * @param state
   * @param block
   * @throws BLSException
   */
  public static void verify_signature(BeaconState state, BeaconBlock block)
      throws IllegalStateException, IllegalArgumentException, BLSException,
          BlockProcessingException {
    // Let block_without_signature_root be the hash_tree_root of block where
    //   block.signature is set to EMPTY_SIGNATURE.
    block.setSignature(EMPTY_SIGNATURE);
    Bytes32 blockWithoutSignatureRootHash = hash_tree_root(block.toBytes());

    // Let proposal_root = hash_tree_root(ProposalSignedData(state.slot,
    //   BEACON_CHAIN_SHARD_NUMBER, block_without_signature_root)).
    ProposalSignedData proposalSignedData =
        new ProposalSignedData(
            state.getSlot(), Constants.BEACON_CHAIN_SHARD_NUMBER, blockWithoutSignatureRootHash);
    Bytes32 proposalRoot = hash_tree_root(proposalSignedData.toBytes());

    // Verify that bls_verify(pubkey=state.validator_registry[get_beacon_proposer_index(state,
    //   state.slot)].pubkey, message=proposal_root, signature=block.signature,
    //   domain=get_domain(state.fork, state.slot, DOMAIN_PROPOSAL)) is valid.
    int proposerIndex = BeaconStateUtil.get_beacon_proposer_index(state, state.getSlot());
    BLSPublicKey pubkey = state.getValidator_registry().get(proposerIndex).getPubkey();
    UnsignedLong domain = get_domain(state.getFork(), get_current_epoch(state), DOMAIN_PROPOSAL);

    checkArgument(
        bls_verify(pubkey, proposalRoot, block.getSignature(), domain), "verify signature failed");
  }

  /**
   * Spec: https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#randao
   *
   * @param state
   * @param block
   */
  public static void verify_and_update_randao(BeaconState state, BeaconBlock block)
      throws IllegalStateException, IllegalArgumentException, BlockProcessingException {

    UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);
    Bytes32 currentEpochBytes = Bytes32.leftPad(Bytes.ofUnsignedLong(currentEpoch.longValue()));
    // - Let proposer = state.validator_registry[get_beacon_proposer_index(state, state.slot)].
    // - Verify that bls_verify(pubkey=proposer.pubkey,
    //    message=int_to_bytes32(get_current_epoch(state)), signature=block.randao_reveal,
    //    domain=get_domain(state.fork, get_current_epoch(state), DOMAIN_RANDAO)).
    if (!block.getState_root().equals(Bytes32.ZERO)) {
      checkArgument(
          verify_randao(state, block, currentEpoch, currentEpochBytes), "verify randao failed");
    }

    // - Set state.latest_randao_mixes[get_current_epoch(state) % LATEST_RANDAO_MIXES_LENGTH]
    //    = xor(get_randao_mix(state, get_current_epoch(state)), hash(block.randao_reveal)).
    int randaoMixesIndex =
        toIntExact(currentEpoch.longValue()) % Constants.LATEST_RANDAO_MIXES_LENGTH;
    Bytes32 newLatestRandaoMixes =
        get_randao_mix(state, currentEpoch).xor(Hash.keccak256(block.getRandao_reveal().toBytes()));
    state.getLatest_randao_mixes().set(randaoMixesIndex, newLatestRandaoMixes);
  }
  /**
   * Spec: https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#eth1-data
   *
   * @param state
   * @param block
   */
  public static void update_eth1_data(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    // If block.eth1_data equals eth1_data_vote.eth1_data for some eth1_data_vote
    //   in state.eth1_data_votes, set eth1_data_vote.vote_count += 1.
    boolean exists = false;
    List<Eth1DataVote> votes = state.getEth1_data_votes();
    for (Eth1DataVote vote : votes) {
      if (block.getEth1_data().equals(vote.getEth1_data())) {
        exists = true;
        UnsignedLong voteCount = vote.getVote_count();
        vote.setVote_count(voteCount.plus(UnsignedLong.ONE));
        break;
      }
    }

    // Otherwise, append to state.eth1_data_votes
    //   a new Eth1DataVote(eth1_data=block.eth1_data, vote_count=1).
    if (!exists) {
      votes.add(new Eth1DataVote(block.getEth1_data(), UnsignedLong.ONE));
    }
  }

  /**
   * Spec:
   * https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#proposer-slashings-1
   *
   * @param state
   * @param block
   */
  public static void proposer_slashing(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    // Verify that len(block.body.proposer_slashings) <= MAX_PROPOSER_SLASHINGS
    checkArgument(block.getBody().getProposer_slashings().size() <= MAX_PROPOSER_SLASHINGS);

    // For each proposer_slashing in block.body.proposer_slashings:
    for (ProposerSlashing proposer_slashing : block.getBody().getProposer_slashings()) {
      // - Let proposer = state.validator_registry[proposer_slashing.proposer_index]
      Validator proposer =
          state
              .getValidator_registry()
              .get(toIntExact(proposer_slashing.getProposer_index().longValue()));

      // - Verify that proposer_slashing.proposal_data_1.slot ==
      //     proposer_slashing.proposal_data_2.slot
      checkArgument(
          proposer_slashing
              .getProposal_data_1()
              .getSlot()
              .equals(proposer_slashing.getProposal_data_2().getSlot()));

      // - Verify that proposer_slashing.proposal_data_1.shard ==
      //     proposer_slashing.proposal_data_2.shard
      checkArgument(
          proposer_slashing
              .getProposal_data_1()
              .getShard()
              .equals(proposer_slashing.getProposal_data_2().getShard()));

      // - Verify that proposer_slashing.proposal_data_1.block_root !=
      //     proposer_slashing.proposal_data_2.block_root
      checkArgument(
          !Objects.equals(
              proposer_slashing.getProposal_data_1().getBlock_root(),
              proposer_slashing.getProposal_data_2().getBlock_root()));

      // - Verify that proposer.penalized_epoch > get_current_epoch(state)
      checkArgument(proposer.getPenalized_epoch().compareTo(get_current_epoch(state)) > 0);

      // - Verify that bls_verify(pubkey=proposer.pubkey,
      //     message=hash_tree_root(proposer_slashing.proposal_data_1),
      //     signature=proposer_slashing.proposal_signature_1, domain=get_domain(state.fork,
      //     slot_to_epoch(proposer_slashing.proposal_data_1.slot), DOMAIN_PROPOSAL)) is valid.
      checkArgument(
          bls_verify(
              proposer.getPubkey(),
              hash_tree_root(proposer_slashing.getProposal_data_1().toBytes()),
              proposer_slashing.getProposal_signature_1(),
              get_domain(
                  state.getFork(),
                  slot_to_epoch(proposer_slashing.getProposal_data_1().getSlot()),
                  DOMAIN_PROPOSAL)));

      // - Verify that bls_verify(pubkey=proposer.pubkey,
      //     message=hash_tree_root(proposer_slashing.proposal_data_2),
      //     signature=proposer_slashing.proposal_signature_2, domain=get_domain(state.fork,
      //     slot_to_epoch(proposer_slashing.proposal_data_2.slot), DOMAIN_PROPOSAL)) is valid.
      checkArgument(
          bls_verify(
              proposer.getPubkey(),
              hash_tree_root(proposer_slashing.getProposal_data_2().toBytes()),
              proposer_slashing.getProposal_signature_2(),
              get_domain(
                  state.getFork(),
                  slot_to_epoch(proposer_slashing.getProposal_data_2().getSlot()),
                  DOMAIN_PROPOSAL)));

      // - Run penalize_validator(state, proposer_slashing.proposer_index)
      penalize_validator(state, proposer_slashing.getProposer_index().intValue());
    }
  }

  /**
   * @param state
   * @param block
   * @see
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#attester-slashings-1
   */
  public static void attester_slashing(BeaconState state, BeaconBlock block)
      throws IllegalArgumentException, BlockProcessingException {
    // Verify that len(block.body.attester_slashings) <= MAX_ATTESTER_SLASHINGS
    checkArgument(block.getBody().getAttester_slashings().size() <= MAX_ATTESTER_SLASHINGS);

    // For each attester_slashing in block.body.attester_slashings:
    for (AttesterSlashing attester_slashing : block.getBody().getAttester_slashings()) {
      // - Let slashable_attestation_1 = attester_slashing.slashable_attestation_1
      // - Let slashable_attestation_2 = attester_slashing.slashable_attestation_2
      SlashableAttestation slashable_attestation_1 = attester_slashing.getSlashable_attestation_1();
      SlashableAttestation slashable_attestation_2 = attester_slashing.getSlashable_attestation_2();

      // - Verify that slashable_attestation_1.data != slashable_attestation_2.data
      checkArgument(
          !Objects.equals(slashable_attestation_1.getData(), slashable_attestation_2.getData()));

      // - Verify that is_double_vote(slashable_attestation_1.data, slashable_attestation_2.data)
      //     or is_surround_vote(slashable_attestation_1.data, slashable_attestation_2.data)
      checkArgument(
          is_double_vote(slashable_attestation_1.getData(), slashable_attestation_2.getData())
              || is_surround_vote(
                  slashable_attestation_1.getData(), slashable_attestation_2.getData()));

      // - Verify that verify_slashable_attestation(state, slashable_attestation_1)
      checkArgument(verify_slashable_attestation(state, slashable_attestation_1));
      // - Verify that verify_slashable_attestation(state, slashable_attestation_2)
      checkArgument(verify_slashable_attestation(state, slashable_attestation_2));

      // - Let slashable_indices = [index for index in slashable_attestation_1.validator_indices
      //     if index in slashable_attestation_2.validator_indices and
      //     state.validator_registry[index].penalized_epoch > get_current_epoch(state)].
      ArrayList<Integer> slashable_indices = new ArrayList<>();
      for (UnsignedLong index : slashable_attestation_1.getValidator_indices()) {
        if (slashable_attestation_2.getValidator_indices().contains(index)
            && state
                    .getValidator_registry()
                    .get(toIntExact(index.longValue()))
                    .getPenalized_epoch()
                    .compareTo(get_current_epoch(state))
                > 0) {
          slashable_indices.add(index.intValue());
        }
      }

      checkArgument(slashable_indices.size() >= 1);
      for (int index : slashable_indices) {
        penalize_validator(state, index);
      }
    }
  }

  /**
   * @param state
   * @param block
   * @see
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#attestations-1
   */
  public static void processAttestations(BeaconState state, BeaconBlock block)
      throws IllegalArgumentException, BlockProcessingException {
    // Verify that len(block.body.attestations) <= MAX_ATTESTATIONS
    checkArgument(block.getBody().getAttestations().size() <= MAX_ATTESTATIONS);

    // For each attestation in block.body.attestations:
    for (Attestation attestation : block.getBody().getAttestations()) {
      // - Verify that attestation.data.slot
      //     <= state.slot - MIN_ATTESTATION_INCLUSION_DELAY
      //     < attestation.data.slot + EPOCH_LENGTH.
      UnsignedLong attestationDataSlot = attestation.getData().getSlot();
      UnsignedLong slotMinusInclusionDelay =
          state.getSlot().minus(UnsignedLong.valueOf(MIN_ATTESTATION_INCLUSION_DELAY));
      UnsignedLong slotPlusEpochLength =
          attestationDataSlot.plus(UnsignedLong.valueOf(EPOCH_LENGTH));
      checkArgument(
          (attestationDataSlot.compareTo(slotMinusInclusionDelay) <= 0)
              && (slotMinusInclusionDelay.compareTo(slotPlusEpochLength) < 0));

      // - Verify that attestation.data.justified_epoch is equal to state.justified_epoch
      //     if attestation.data.slot >= get_epoch_start_slot(get_current_epoch(state))
      //     else state.previous_justified_epoch.
      if (attestation.getData().getSlot().compareTo(get_epoch_start_slot(get_current_epoch(state)))
          >= 0) {
        checkArgument(
            attestation.getData().getJustified_epoch().equals(state.getJustified_epoch()));
      } else {
        checkArgument(
            attestation.getData().getJustified_epoch().equals(state.getPrevious_justified_epoch()));
      }

      // - Verify that attestation.data.justified_block_root is equal to
      //     get_block_root(state, get_epoch_start_slot(attestation.data.justified_epoch)).
      checkArgument(
          Objects.equals(
              attestation.getData().getJustified_block_root(),
              get_block_root(
                  state, get_epoch_start_slot(attestation.getData().getJustified_epoch()))));

      // - Verify that either attestation.data.latest_crosslink_root or
      // attestation.data.shard_block_root
      //     equals state.latest_crosslinks[shard].shard_block_root.
      checkArgument(
          attestation
                  .getData()
                  .getLatest_crosslink_root()
                  .equals(
                      state
                          .getLatest_crosslinks()
                          .get(attestation.getData().getShard().intValue())
                          .getShard_block_root())
              || attestation
                  .getData()
                  .getShard_block_root()
                  .equals(
                      state
                          .getLatest_crosslinks()
                          .get(attestation.getData().getShard().intValue())
                          .getShard_block_root()));

      // - Verify bitfields and aggregate signature
      checkArgument(verify_bitfields_and_aggregate_signature(attestation, state));

      // - Verify that attestation.data.shard_block_root == ZERO_HASH
      // TO BE REMOVED IN PHASE 1
      checkArgument(attestation.getData().getShard_block_root().equals(ZERO_HASH));

      // - Append PendingAttestation(data=attestation.data,
      //     aggregation_bitfield=attestation.aggregation_bitfield,
      //     custody_bitfield=attestation.custody_bitfield, inclusion_slot=state.slot) to
      //     state.latest_attestations.
      PendingAttestation pendingAttestation =
          new PendingAttestation(
              attestation.getAggregation_bitfield(),
              attestation.getData(),
              attestation.getCustody_bitfield(),
              state.getSlot());
      state.getLatest_attestations().add(pendingAttestation);
    }
  }

  @VisibleForTesting
  static boolean verify_randao(
      BeaconState state, BeaconBlock block, UnsignedLong currentEpoch, Bytes32 currentEpochBytes)
      throws BlockProcessingException {
    // Let proposer = state.validator_registry[get_beacon_proposer_index(state, state.slot)].
    int proposerIndex = BeaconStateUtil.get_beacon_proposer_index(state, state.getSlot());
    Validator proposer = state.getValidator_registry().get(proposerIndex);

    // Verify that bls_verify(pubkey=proposer.pubkey,
    //   message=int_to_bytes32(get_current_epoch(state)), signature=block.randao_reveal,
    //   domain=get_domain(state.fork, get_current_epoch(state), DOMAIN_RANDAO)).
    UnsignedLong domain = get_domain(state.getFork(), currentEpoch, DOMAIN_RANDAO);
    return bls_verify(proposer.getPubkey(), currentEpochBytes, block.getRandao_reveal(), domain);
  }

  /**
   * Helper function for attestations.
   *
   * @param attestation
   * @param state
   * @return true if bitfields and aggregate signature verified. Otherwise, false.
   */
  private static boolean verify_bitfields_and_aggregate_signature(
      Attestation attestation, BeaconState state) throws BlockProcessingException {
    // NOTE: The spec defines this verification in terms of the custody bitfield length,
    //   however because we've implemented the bitfield as a static Bytes32 value
    //   instead of a variable length bitfield, checking against Bytes32.ZERO will suffice.
    checkArgument(
        Objects.equals(
            attestation.getCustody_bitfield(), Bytes32.ZERO)); // [TO BE REMOVED IN PHASE 1]
    checkArgument(!Objects.equals(attestation.getAggregation_bitfield(), Bytes32.ZERO));

    List<List<Integer>> crosslink_committees = new ArrayList<>();
    for (CrosslinkCommittee crosslink_committee :
        get_crosslink_committees_at_slot(state, attestation.getData().getSlot())) {
      if (Objects.equals(crosslink_committee.getShard(), attestation.getData().getShard())) {
        crosslink_committees.add(crosslink_committee.getCommittee());
      }
    }
    List<Integer> crosslink_committee = crosslink_committees.get(0);

    for (int i = 0; i < crosslink_committee.size(); i++) {
      checkArgument(
          get_bitfield_bit(attestation.getAggregation_bitfield(), i) != 0b0
              || get_bitfield_bit(attestation.getCustody_bitfield(), i) == 0b0);
    }

    List<Integer> participants =
        get_attestation_participants(
            state, attestation.getData(), attestation.getAggregation_bitfield().toArray());
    List<Integer> custody_bit_1_participants =
        get_attestation_participants(
            state, attestation.getData(), attestation.getCustody_bitfield().toArray());
    List<Integer> custody_bit_0_participants = new ArrayList<>();
    for (Integer participant : participants) {
      if (custody_bit_1_participants.indexOf(participant) != -1) {
        custody_bit_0_participants.add(participant);
      }
    }

    List<BLSPublicKey> pubkey0 = new ArrayList<>();
    for (int i = 0; i < custody_bit_0_participants.size(); i++) {
      pubkey0.add(state.getValidator_registry().get(i).getPubkey());
    }

    List<BLSPublicKey> pubkey1 = new ArrayList<>();
    for (int i = 0; i < custody_bit_1_participants.size(); i++) {
      pubkey1.add(state.getValidator_registry().get(i).getPubkey());
    }

    checkArgument(
        bls_verify_multiple(
            Arrays.asList(bls_aggregate_pubkeys(pubkey0), bls_aggregate_pubkeys(pubkey1)),
            Arrays.asList(
                hash_tree_root(
                    new AttestationDataAndCustodyBit(attestation.getData(), false).toBytes()),
                hash_tree_root(
                    new AttestationDataAndCustodyBit(attestation.getData(), true).toBytes())),
            attestation.getAggregate_signature(),
            get_domain(
                state.getFork(),
                slot_to_epoch(attestation.getData().getSlot()),
                DOMAIN_ATTESTATION)));

    return true;
  }

  /**
   * @param state
   * @param block
   * @see https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#deposits-1
   */
  public static void processDeposits(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    // Verify that len(block.body.deposits) <= MAX_DEPOSITS
    checkArgument(block.getBody().getDeposits().size() <= MAX_DEPOSITS);

    // SPEC TODO: add logic to ensure that deposits from 1.0 chain are processed in order
    // SPEC TODO: update the call to verify_merkle_branch below if it needs to change
    //   after we process deposits in order

    // For each deposit in block.body.deposits:
    for (Deposit deposit : block.getBody().getDeposits()) {
      // - Let serialized_deposit_data be the serialized form of deposit.deposit_data.
      //     It should be 8 bytes for deposit_data.amount followed by 8 bytes for
      //     deposit_data.timestamp and then the DepositInput bytes. That is,
      //     it should match deposit_data in the Ethereum 1.0 deposit contract of which
      //     the hash was placed into the Merkle tree.
      Bytes serialized_deposit_data = deposit.getDeposit_data().toBytes();

      // - Vadliate verify_merkle_branch(hash(serialized_deposit_data), deposit.branch,
      //     DEPOSIT_CONTRACT_TREE_DEPTH, deposit.index, state.latest_eth1_data.deposit_root)
      checkArgument(
          verify_merkle_branch(
              Hash.keccak256(serialized_deposit_data),
              deposit.getBranch(),
              DEPOSIT_CONTRACT_TREE_DEPTH,
              toIntExact(deposit.getIndex().longValue()),
              state.getLatest_eth1_data().getDeposit_root()));

      // - Run process_deposit
      process_deposit(
          state,
          deposit.getDeposit_data().getDeposit_input().getPubkey(),
          deposit.getDeposit_data().getAmount(),
          deposit.getDeposit_data().getDeposit_input().getProof_of_possession(),
          deposit.getDeposit_data().getDeposit_input().getWithdrawal_credentials());
    }
  }

  /**
   * @param state
   * @param block
   * @see https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#exits-1
   */
  public static void processExits(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    // Verify that len(block.body.exits) <= MAX_EXITS
    checkArgument(block.getBody().getExits().size() <= Constants.MAX_EXITS);

    // For each exit in block.body.exits:
    for (Exit exit : block.getBody().getExits()) {
      // - Let validator = state.validator_registry[exit.validator_index]
      Validator validator =
          state.getValidator_registry().get(toIntExact(exit.getValidator_index().longValue()));

      // - Verify that validator.exit_epoch > get_entry_exit_effect_epoch(get_current_epoch(state))
      checkArgument(
          validator.getExit_epoch().compareTo(get_entry_exit_effect_epoch(get_current_epoch(state)))
              > 0);

      // - Verify that get_current_epoch(state) >= exit.epoch
      checkArgument(get_current_epoch(state).compareTo(exit.getEpoch()) >= 0);

      // - Let exit_message = hash_tree_root(Exit(epoch=exit.epoch,
      //     validator_index=exit.validator_index, signature=EMPTY_SIGNATURE))
      Bytes32 exit_message =
          hash_tree_root(
              new Exit(exit.getEpoch(), exit.getValidator_index(), EMPTY_SIGNATURE).toBytes());
      // - Verify that bls_verify(
      //     pubkey=validator.pubkey, message=exit_message, signature=exit.signature,
      //     domain=get_domain(state.fork, exit.epoch, DOMAIN_EXIT)) is valid
      checkArgument(
          bls_verify(
              validator.getPubkey(),
              exit_message,
              exit.getSignature(),
              get_domain(state.getFork(), exit.getEpoch(), DOMAIN_EXIT)));

      // - Run initiate_validator_exit(state, exit.validator_index)
      initiate_validator_exit(state, toIntExact(exit.getValidator_index().longValue()));
    }
  }

  /**
   * Verify that the given ``leaf`` is on the merkle branch ``branch``.
   *
   * @param leaf
   * @param branch
   * @param depth
   * @param index
   * @param root
   * @return
   */
  private static boolean verify_merkle_branch(
      Bytes32 leaf, List<Bytes32> branch, int depth, int index, Bytes32 root) {
    Bytes32 value = leaf;
    for (int i = 0; i < depth; i++) {
      if (index / Math.pow(2, i) % 2 == 0) {
        value = Hash.keccak256(Bytes.concatenate(branch.get(i), value));
      } else {
        value = Hash.keccak256(Bytes.concatenate(value, branch.get(i)));
      }
    }
    return value.equals(root);
  }
}

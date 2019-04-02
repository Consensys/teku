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
import static tech.pegasys.artemis.datastructures.Constants.*;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.*;
import static tech.pegasys.artemis.util.bls.BLSAggregate.bls_aggregate_pubkeys;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify_multiple;
import static tech.pegasys.artemis.util.hashtree.HashTreeUtil.hash_tree_root;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataVote;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SlashableAttestation;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.BeaconBlockUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSException;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public final class BlockProcessorUtil {

  private static final ALogger LOG = new ALogger(BlockProcessorUtil.class.getName());

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

  public static void process_block_header(BeaconState state, BeaconBlock block) {
    checkArgument(verify_slot(state, block), "Slots don't match");
    checkArgument(block.getPrevious_block_root() ==
            hash_tree_root(state.getLatest_block_header().toBytes()), "Parent doesn't matches");

    // Save the current block as the new latest block
    state.setLatest_block_header(BeaconBlockUtil.get_temporary_block_header(block));

    // Verify proposer signature
    Validator proposer = state.getValidator_registry()
            .get(get_beacon_proposer_index(state, state.getSlot()));
    checkArgument(bls_verify(
            proposer.getPubkey(),
            block.signedRoot("signature"),
            block.getSignature(),
            get_domain(
                    state.getFork(),
                    get_current_epoch(state),
                    Constants.DOMAIN_BEACON_BLOCK
            )), "Proposer signature invalid";
  }

  public static void process_randao(BeaconState state, BeaconBlock block) {
    Validator proposer = state.getValidator_registry()
            .get(get_beacon_proposer_index(state, state.getSlot()));

    checkArgument(bls_verify(
            proposer.getPubkey(),
            hash_tree_root(get_current_epoch(state)),
            block.getBody().getRandao_reveal(),
            get_domain(state.getFork(), get_current_epoch(state), DOMAIN_RANDAO)
            ), "Provided randao value is invalid");

    // Mix Randao value in
    int index = get_current_epoch(state).mod(UnsignedLong.valueOf(Constants.LATEST_RANDAO_MIXES_LENGTH)).intValue();
    Bytes32 newRandaoMix = get_randao_mix(state, get_current_epoch(state))
            .xor(Hash.keccak256(block.getBody().getRandao_reveal().toBytes()));
    state.getLatest_randao_mixes().set(index, newRandaoMix);
  }

  public static void process_eth1_data(BeaconState state, BeaconBlock block) {
    for (Eth1DataVote eth1DataVote : state.getEth1_data_votes()) {
      // If someone else has already voted for the same hash, add to its counter
      if (eth1DataVote.getEth1_data() == block.getBody().getEth1_data()) {
        eth1DataVote.setVote_count(eth1DataVote.getVote_count().plus(UnsignedLong.ONE));
        return;
      }
    }

    // If we're seeing this hash for the first time, make a new counter
    state.getEth1_data_votes().add(new Eth1DataVote(block.getBody().getEth1_data(), UnsignedLong.ONE))
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
      throws BlockProcessingException {
    try {
      // Let proposal = Proposal(block.slot, BEACON_CHAIN_SHARD_NUMBER,
      //   signed_root(block, "signature"), block.signature).
      Proposal proposal =
          new Proposal(
              UnsignedLong.fromLongBits(block.getSlot()),
              Constants.BEACON_CHAIN_SHARD_NUMBER,
              block.signedRoot("signature"),
              block.getSignature());

      // Verify that bls_verify(pubkey=state.validator_registry[get_beacon_proposer_index(state,
      //   state.slot)].pubkey, message_hash=signed_root(proposal, "signature"),
      // signature=block.signature,
      //   domain=get_domain(state.fork, state.slot, DOMAIN_PROPOSAL)) is valid.
      int proposerIndex = BeaconStateUtil.get_beacon_proposer_index(state, state.getSlot());
      BLSPublicKey pubkey = state.getValidator_registry().get(proposerIndex).getPubkey();
      UnsignedLong domain = get_domain(state.getFork(), get_current_epoch(state), DOMAIN_PROPOSAL);
      Bytes32 messageHash = proposal.signedRoot("signature");

      checkArgument(
          bls_verify(pubkey, messageHash, block.getSignature(), domain), "verify signature failed");
    } catch (IllegalStateException | IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in verify_signature()");
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Spec: https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#randao
   *
   * @param state
   * @param block
   */
  public static void verify_and_update_randao(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    try {
      UnsignedLong currentEpoch = BeaconStateUtil.get_current_epoch(state);
      Bytes32 messageHash = hash_tree_root(int_to_bytes(currentEpoch.longValue(), 8));
      // - Let proposer = state.validator_registry[get_beacon_proposer_index(state, state.slot)].
      // - Verify that bls_verify(pubkey=proposer.pubkey,
      //    message=int_to_bytes32(get_current_epoch(state)), signature=block.randao_reveal,
      //    domain=get_domain(state.fork, get_current_epoch(state), DOMAIN_RANDAO)).
      checkArgument(
          verify_randao(state, block, currentEpoch, messageHash), "in verify_and_update_randao()");

      // - Set state.latest_randao_mixes[get_current_epoch(state) % LATEST_RANDAO_MIXES_LENGTH]
      //    = xor(get_randao_mix(state, get_current_epoch(state)), hash(block.randao_reveal)).
      int randaoMixesIndex =
          toIntExact(currentEpoch.longValue()) % Constants.LATEST_RANDAO_MIXES_LENGTH;
      Bytes32 newLatestRandaoMixes =
          get_randao_mix(state, currentEpoch)
              .xor(Hash.keccak256(block.getRandao_reveal().toBytes()));
      state.getLatest_randao_mixes().set(randaoMixesIndex, newLatestRandaoMixes);
    } catch (IllegalStateException | IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in verify_and_update_randao()");
      throw new BlockProcessingException(e);
    }
  }
  /**
   * Spec: https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#eth1-data
   *
   * @param state
   * @param block
   */
  public static void update_eth1_data(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    // If there exists an `eth1_data_vote` in `states.eth1_data_votes` for which
    // `eth1_data_vote.eth1_data == block.eth1_data`
    //  (there will be at most one), set `eth1_data_vote.vote_count += 1`.
    boolean exists = false;
    List<Eth1DataVote> votes = state.getEth1_data_votes();
    for (Eth1DataVote vote : votes) {
      if (vote.getEth1_data().equals(block.getEth1_data())) {
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
    try {
      // Verify that len(block.body.proposer_slashings) <= MAX_PROPOSER_SLASHINGS
      checkArgument(
          block.getBody().getProposer_slashings().size() <= MAX_PROPOSER_SLASHINGS,
          "checkArgument threw and exception in proposer_slashing()");

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
                .getHeader_1()
                .getSlot()
                .equals(proposer_slashing.getHeader_2().getSlot()),
            "Slot is not the same in proposer slashings");

        // - Verify that proposer_slashing.proposal_data_1.block_root !=
        //     proposer_slashing.proposal_data_2.block_root
        checkArgument(
            !Objects.equals(
                hash_tree_root(proposer_slashing.getHeader_1().toBytes()),
                hash_tree_root(proposer_slashing.getHeader_2().toBytes())),
            "Roots are the same in proposer slashings");

        // - Verify that proposer.slashed == false
        checkArgument(!proposer.isSlashed(),"Proposer is already slashed in proposer slashings");

        // - Verify that bls_verify(pubkey=proposer.pubkey,
        //     message=hash_tree_root(proposer_slashing.proposal_data_1),
        //     signature=proposer_slashing.proposal_signature_1, domain=get_domain(state.fork,
        //     slot_to_epoch(proposer_slashing.proposal_data_1.slot), DOMAIN_PROPOSAL)) is valid.
        checkArgument(
            bls_verify(
                proposer.getPubkey(),
                hash_tree_root(proposer_slashing.getHeader_1().toBytes()),
                proposer_slashing.getHeader_1().getSignature(),
                get_domain(
                    state.getFork(),
                    slot_to_epoch(proposer_slashing.getHeader_1().getSlot()),
                    DOMAIN_BEACON_BLOCK)),
            "BLSVerify fail for proposal header 1");

        // - Verify that bls_verify(pubkey=proposer.pubkey,
        //     message=hash_tree_root(proposer_slashing.proposal_data_2),
        //     signature=proposer_slashing.proposal_signature_2, domain=get_domain(state.fork,
        //     slot_to_epoch(proposer_slashing.proposal_data_2.slot), DOMAIN_PROPOSAL)) is valid.
        checkArgument(
            bls_verify(
                proposer.getPubkey(),
                hash_tree_root(proposer_slashing.getHeader_2().toBytes()),
                proposer_slashing.getHeader_2().getSignature(),
                get_domain(
                    state.getFork(),
                    slot_to_epoch(proposer_slashing.getHeader_2().getSlot()),
                    DOMAIN_BEACON_BLOCK)),
            "BLSVerify fail for proposal header 2");

        // - Run penalize_validator(state, proposer_slashing.proposer_index)
        slash_validator(state, proposer_slashing.getProposer_index().intValue());
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in proposer_slashing()");
      throw new BlockProcessingException(e);
    }
  }

  /**
   * @param state
   * @param block
   * @see <a href=
   *     "https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#attester-slashings-1">spec</a>
   */
  public static void attester_slashing(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    try {
      // Verify that len(block.body.attester_slashings) <= MAX_ATTESTER_SLASHINGS
      checkArgument(
          block.getBody().getAttester_slashings().size() <= MAX_ATTESTER_SLASHINGS,
          "Number of attester slashings more than limit in attester slashing");

      // For each attester_slashing in block.body.attester_slashings:
      for (AttesterSlashing attester_slashing : block.getBody().getAttester_slashings()) {
        // - Let slashable_attestation_1 = attester_slashing.slashable_attestation_1
        // - Let slashable_attestation_2 = attester_slashing.slashable_attestation_2
        SlashableAttestation slashable_attestation_1 =
            attester_slashing.getSlashable_attestation_1();
        SlashableAttestation slashable_attestation_2 =
            attester_slashing.getSlashable_attestation_2();

        // - Verify that slashable_attestation_1.data != slashable_attestation_2.data
        checkArgument(
            !Objects.equals(slashable_attestation_1.getData(), slashable_attestation_2.getData()),
            "Data are equal in attester slashing");

        // - Verify that is_double_vote(slashable_attestation_1.data, slashable_attestation_2.data)
        //     or is_surround_vote(slashable_attestation_1.data, slashable_attestation_2.data)
        checkArgument(
            is_double_vote(slashable_attestation_1.getData(), slashable_attestation_2.getData())
                || is_surround_vote(
                    slashable_attestation_1.getData(), slashable_attestation_2.getData()),
            "Neither double nor surround vote in attester slashing");

        // - Verify that verify_slashable_attestation(state, slashable_attestation_1)
        checkArgument(
            verify_slashable_attestation(state, slashable_attestation_1),
            "Not slashable in attester_slashing() 1");
        // - Verify that verify_slashable_attestation(state, slashable_attestation_2)
        checkArgument(
            verify_slashable_attestation(state, slashable_attestation_2),
            "Not slashable in attester_slashing() 2");

        // - Let slashable_indices = [index for index in slashable_attestation_1.validator_indices
        //     if index in slashable_attestation_2.validator_indices and
        //     state.validator_registry[index].slashed == false.
        ArrayList<Integer> slashable_indices = new ArrayList<>();
        for (UnsignedLong index : slashable_attestation_1.getValidator_indices()) {
          if (slashable_attestation_2.getValidator_indices().contains(index)
              && !state.getValidator_registry().get(toIntExact(index.longValue())).isSlashed()) {
            slashable_indices.add(index.intValue());
          }
        }

        checkArgument(
            slashable_indices.size() >= 1,
            "Could not find slashable indices");
        for (int index : slashable_indices) {
          slash_validator(state, index);
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in attester_slashing()");
      throw new BlockProcessingException(e);
    }
  }

  /**
   * @param state
   * @param block
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#attestations-1">spec</a>
   */
  public static void processAttestations(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    try {
      // Verify that len(block.body.attestations) <= MAX_ATTESTATIONS
      checkArgument(
          block.getBody().getAttestations().size() <= MAX_ATTESTATIONS,
          "Number of attestations more than limit in processAttestations()");

      // For each attestation in block.body.attestations:
      for (Attestation attestation : block.getBody().getAttestations()) {
        // - Verify that attestation.data.slot
        //     <= state.slot - MIN_ATTESTATION_INCLUSION_DELAY
        //     < attestation.data.slot + SLOTS_PER_EPOCH.
        UnsignedLong attestationDataSlot = attestation.getData().getSlot();
        checkArgument(
            attestationDataSlot.compareTo(UnsignedLong.valueOf(Constants.GENESIS_EPOCH)) >= 0);
        checkArgument(
            attestationDataSlot
                    .plus(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY))
                    .compareTo(state.getSlot())
                <= 0);
        checkArgument(
            state
                    .getSlot()
                    .compareTo(attestationDataSlot.plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH)))
                <= 0);

        // Verify that attestation.data.justified_epoch is equal to state.justified_epoch
        // if slot_to_epoch(attestation.data.slot + 1) >= get_current_epoch(state) else
        // state.previous_justified_epoch.
        if (slot_to_epoch(attestation.getData().getSlot()).compareTo(get_current_epoch(state))
            >= 0) {
          checkArgument(
              attestation.getData().getSource_epoch().equals(state.getCurrent_justified_epoch()),
              "Current epoch attestation epoch number error");
          checkArgument(
                  attestation.getData().getSource_root().equals(state.getCurrent_justified_root()),
                  "Current epoch attestation root error");
        } else {
          checkArgument(
                  attestation.getData().getSource_epoch().equals(state.getPrevious_justified_epoch()),
                  "Previous epoch attestation epoch number error");
          checkArgument(
                  attestation.getData().getSource_root().equals(state.getPrevious_justified_root()),
                  "Previous epoch attestation root error");
        }

        // Check that the crosslink data is valid
        Crosslink latest_crosslink = state
                    .getLatest_crosslinks()
                    .get(toIntExact(attestation.getData().getShard().longValue()));
        checkArgument(latest_crosslink.equals(
                attestation.getData().getPrevious_crosslink())
                || latest_crosslink.equals(
                        new Crosslink(
                            slot_to_epoch(attestationDataSlot),
                            attestation.getData().getCrosslink_data_root())),
                "Crosslink data is invalid");

        // - Verify bitfields and aggregate signature
        checkArgument(
            verify_bitfields_and_aggregate_signature(attestation, state),
            "Verify bitfield and aggregate signature has failed");

        // - Verify that attestation.data.shard_block_root == ZERO_HASH
        // TO BE REMOVED IN PHASE 1
        checkArgument(
            attestation.getData().getCrosslink_data_root().equals(ZERO_HASH),
            "Crosslink data root is not zero");

        // - Apply the attestation
        PendingAttestation pendingAttestation =
            new PendingAttestation(
                attestation.getAggregation_bitfield(),
                attestation.getData(),
                attestation.getCustody_bitfield(),
                state.getSlot());

        if (slot_to_epoch(attestation.getData().getSlot()).compareTo(get_current_epoch(state)) == 0) {
          state.getCurrent_epoch_attestations().add(pendingAttestation);
        } else if (slot_to_epoch(attestation.getData().getSlot()).compareTo(get_previous_epoch(state)) == 0){
          state.getCurrent_epoch_attestations().add(pendingAttestation);
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in processAttestations()");
      throw new BlockProcessingException(e);
    }
  }

  static boolean verify_randao(
      BeaconState state, BeaconBlock block, UnsignedLong currentEpoch, Bytes32 currentEpochBytes)
      throws IllegalStateException {
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
    checkArgument(
        Objects.equals(
            attestation.getCustody_bitfield(),
            Bytes.wrap(new byte[attestation.getCustody_bitfield().size()])),
        "checkArgument threw and exception in verify_bitfields_and_aggregate_signature()"); // [TO
    // BE
    // REMOVED IN PHASE 1]
    checkArgument(
        !Objects.equals(
            attestation.getAggregation_bitfield(),
            Bytes.wrap(new byte[attestation.getAggregation_bitfield().size()])),
        "checkArgument threw and exception in verify_bitfields_and_aggregate_signature()");

    // Get the committee for the specific shard that this attestation is for
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
              || get_bitfield_bit(attestation.getCustody_bitfield(), i) == 0b0,
          "checkArgument threw and exception in verify_bitfields_and_aggregate_signature()");
    }

    List<Integer> participants =
        get_attestation_participants(
            state, attestation.getData(), attestation.getAggregation_bitfield().toArray());
    List<Integer> custody_bit_1_participants =
        get_attestation_participants(
            state, attestation.getData(), attestation.getCustody_bitfield().toArray());
    List<Integer> custody_bit_0_participants = new ArrayList<>();
    for (Integer participant : participants) {
      if (custody_bit_1_participants.indexOf(participant) == -1) {
        custody_bit_0_participants.add(participant);
      }
    }

    List<BLSPublicKey> pubkey0 = new ArrayList<>();
    for (int i = 0; i < custody_bit_0_participants.size(); i++) {
      pubkey0.add(state.getValidator_registry().get(custody_bit_0_participants.get(i)).getPubkey());
    }

    List<BLSPublicKey> pubkey1 = new ArrayList<>();
    for (int i = 0; i < custody_bit_1_participants.size(); i++) {
      pubkey1.add(state.getValidator_registry().get(custody_bit_1_participants.get(i)).getPubkey());
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
                DOMAIN_ATTESTATION)),
        "checkArgument threw and exception in verify_bitfields_and_aggregate_signature()");

    return true;
  }

  /**
   * @param state
   * @param block
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#deposits-1">spec</a>
   */
  public static void processDeposits(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    try {
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

        checkArgument(Objects.equals(state.getDeposit_index(), deposit.getIndex()));

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
        process_deposit(state, deposit);

        state.setDeposit_index(state.getDeposit_index().plus(UnsignedLong.ONE));
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in processExits()");
      throw new BlockProcessingException(e);
    }
  }

  /**
   * @param state
   * @param block
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#exits-1">spec</a>
   */
  public static void processVoluntaryExits(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    try {
      // Verify that len(block.body.voluntary_exits) <= MAX_VOLUNTARY_EXITS
      checkArgument(
          block.getBody().getVoluntaryExits().size() <= Constants.MAX_VOLUNTARY_EXITS,
          "checkArgument threw and exception in processExits()");

      // For each exit in block.body.voluntaryExits:
      for (VoluntaryExit voluntaryExit : block.getBody().getVoluntaryExits()) {
        // - Let validator = state.validator_registry[exit.validator_index]
        Validator validator =
            state
                .getValidator_registry()
                .get(toIntExact(voluntaryExit.getValidator_index().longValue()));

        // - Verify that validator.exit_epoch >
        // get_entry_exit_effect_epoch(get_current_epoch(state))
        checkArgument(
            validator
                    .getExit_epoch()
                    .compareTo(get_entry_exit_effect_epoch(get_current_epoch(state)))
                > 0,
            "checkArgument threw and exception in processExits()");

        // - Verify that get_current_epoch(state) >= exit.epoch
        checkArgument(
            get_current_epoch(state).compareTo(voluntaryExit.getEpoch()) >= 0,
            "checkArgument threw and exception in processExits()");

        // - Verify that bls_verify(
        //     pubkey=validator.pubkey, message_hash=signed_root(exit,"signatre"),
        // signature=exit.signature,
        //     domain=get_domain(state.fork, exit.epoch, DOMAIN_EXIT)) is valid
        checkArgument(
            bls_verify(
                validator.getPubkey(),
                voluntaryExit.signedRoot("signature"),
                voluntaryExit.getSignature(),
                get_domain(state.getFork(), voluntaryExit.getEpoch(), DOMAIN_VOLUNTARY_EXIT)),
            "checkArgument threw and exception in processExits()");

        // - Run initiate_validator_exit(state, exit.validator_index)
        initiate_validator_exit(state, toIntExact(voluntaryExit.getValidator_index().longValue()));
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in processExits()");
      throw new BlockProcessingException(e);
    }
  }

  /**
   * @param state
   * @param block
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.3/specs/core/0_beacon-chain.md#transfers-1">spec
   *     </a>
   */
  public static void processTransfers(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    // Verify that len(block.body.transfers) <= MAX_TRANSFERS and that all transfers are distinct.
    checkArgument(block.getBody().getTransfers().size() <= MAX_TRANSFERS);
    checkArgument(allDistinct(block.getBody().getTransfers()));

    // For each transfer in block.body.transfers:
    for (Transfer transfer : block.getBody().getTransfers()) {
      // - Verify that state.validator_balances[transfer.from] >= transfer.amount
      checkArgument(
          state.getValidator_balances().get(toIntExact(transfer.getSender().longValue())).longValue()
              >= transfer.getAmount().longValue());
      // - Verify that state.validator_balances[transfer.from] >= transfer.fee
      checkArgument(
          state.getValidator_balances().get(toIntExact(transfer.getSender().longValue())).longValue()
              >= transfer.getFee().longValue());
      // - Verify that state.validator_balances[transfer.from] == transfer.amount + transfer.fee or
      //     state.validator_balances[transfer.from]
      //     >= transfer.amount + transfer.fee + MIN_DEPOSIT_AMOUNT
      checkArgument(
          state.getValidator_balances().get(toIntExact(transfer.getSender().longValue())).longValue()
                  == transfer.getAmount().longValue() + transfer.getFee().longValue()
              || state
                      .getValidator_balances()
                      .get(toIntExact(transfer.getSender().longValue()))
                      .longValue()
                  >= transfer.getAmount().longValue()
                      + transfer.getFee().longValue()
                      + MIN_DEPOSIT_AMOUNT);
      // - Verify that transfer.slot == state.slot
      checkArgument(state.getSlot().equals(transfer.getSlot()));
      // - Verify that get_current_epoch(state) >=
      //     state.validator_registry[transfer.from].exit_epoch + MIN_EXIT_EPOCHS_BEFORE_TRANSFER
      checkArgument(
          get_current_epoch(state)
                      .compareTo(
                          state
                              .getValidator_registry()
                              .get(toIntExact(transfer.getSender().longValue()))
                              .getWithdrawal_epoch())
                  >= 0
              || state
                  .getValidator_registry()
                  .get(toIntExact(transfer.getSender().longValue()))
                  .getActivation_epoch()
                  .equals(FAR_FUTURE_EPOCH));
      // - Verify that state.validator_registry[transfer.from].withdrawal_credentials ==
      //     BLS_WITHDRAWAL_PREFIX_BYTE + hash(transfer.pubkey)[1:]
      checkArgument(
          state
              .getValidator_registry()
              .get(toIntExact(transfer.getSender().longValue()))
              .getWithdrawal_credentials()
              .equals(
                  Bytes.concatenate(
                      BLS_WITHDRAWAL_PREFIX_BYTE, transfer.getPubkey().toBytes().slice(1))));
      // - Let transfer_message = hash_tree_root(Transfer(from=transfer.from, to=transfer.to,
      //     amount=transfer.amount, fee=transfer.fee, slot=transfer.slot,
      //     signature=EMPTY_SIGNATURE))
      Bytes32 transfer_message =
          hash_tree_root(
              new Transfer(
                      transfer.getSender(),
                      transfer.getRecipient(),
                      transfer.getAmount(),
                      transfer.getFee(),
                      transfer.getSlot(),
                      transfer.getPubkey(),
                      EMPTY_SIGNATURE)
                  .toBytes());
      // - Perform bls_verify(pubkey=transfer.pubkey, message_hash=transfer_message,
      //     signature=transfer.signature, domain=get_domain(state.fork,
      //     slot_to_epoch(transfer.slot), DOMAIN_TRANSFER))
      checkArgument(
          bls_verify(
              transfer.getPubkey(),
              transfer_message,
              transfer.getSignature(),
              get_domain(
                  state.getFork(), slot_to_epoch(transfer.getSlot()), DOMAIN_TRANSFER)));

      // - Set state.validator_balances[transfer.from] -= transfer.amount + transfer.fee
      UnsignedLong fromBalance =
          state.getValidator_balances().get(toIntExact(transfer.getSender().longValue()));
      fromBalance = fromBalance.minus(transfer.getAmount()).minus(transfer.getFee());
      state.getValidator_balances().set(toIntExact(transfer.getSender().longValue()), fromBalance);

      // - Set state.validator_balances[transfer.to] += transfer.amount
      UnsignedLong toBalance =
          state.getValidator_balances().get(toIntExact(transfer.getSender().longValue()));
      toBalance = toBalance.plus(transfer.getAmount());
      state.getValidator_balances().set(toIntExact(transfer.getRecipient().longValue()), toBalance);

      // - Set state.validator_balances[get_beacon_proposer_index(state, state.slot)]
      //     += transfer.fee
      UnsignedLong proposerBalance =
          state.getValidator_balances().get(get_beacon_proposer_index(state, state.getSlot()));
      proposerBalance = proposerBalance.plus(transfer.getFee());
      state
          .getValidator_balances()
          .set(get_beacon_proposer_index(state, state.getSlot()), proposerBalance);
    }
  }

  private static <T> boolean allDistinct(List<T> list) {
    HashSet<T> set = new HashSet<>();

    for (T t : list) {
      if (set.contains(t)) {
        return false;
      }

      set.add(t);
    }

    return true;
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

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
import static tech.pegasys.artemis.datastructures.Constants.BLS_WITHDRAWAL_PREFIX_BYTE;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_ATTESTATION;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_BEACON_BLOCK;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_RANDAO;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_TRANSFER;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_VOLUNTARY_EXIT;
import static tech.pegasys.artemis.datastructures.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_ATTESTER_SLASHINGS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSITS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_PROPOSER_SLASHINGS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_TRANSFERS;
import static tech.pegasys.artemis.datastructures.Constants.MIN_DEPOSIT_AMOUNT;
import static tech.pegasys.artemis.datastructures.Constants.PERSISTENT_COMMITTEE_PERIOD;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_attestation_participants;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_bitfield_bit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_crosslink_committees_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initiate_validator_exit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_double_vote;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_surround_vote;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.max;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.slash_validator;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.slot_to_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.verify_slashable_attestation;
import static tech.pegasys.artemis.util.bls.BLSAggregate.bls_aggregate_pubkeys;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify_multiple;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;
import net.consensys.cava.ssz.SSZ;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
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
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public final class BlockProcessorUtil {

  private static final ALogger LOG = new ALogger(BlockProcessorUtil.class.getName());

  /**
   * @param state
   * @param block
   */
  public static void process_block_header(BeaconState state, BeaconBlock block) {
    checkArgument(verify_slot(state, block), "Slots don't match");
    checkArgument(
        block
            .getPrevious_block_root()
            .equals(state.getLatest_block_header().signed_root("signature")),
        "Parent doesn't match");

    // Save the current block as the new latest block
    state.setLatest_block_header(BeaconBlockUtil.get_temporary_block_header(block));

    // Verify proposer signature
    // Only verify the proposer's signature if we are processing blocks (not proposing them)
    if (!block.getState_root().equals(Bytes32.ZERO)) {
      Validator proposer =
          state.getValidator_registry().get(get_beacon_proposer_index(state, state.getSlot()));
      checkArgument(
          bls_verify(
              proposer.getPubkey(),
              block.signed_root("signature"),
              block.getSignature(),
              get_domain(state.getFork(), get_current_epoch(state), Constants.DOMAIN_BEACON_BLOCK)),
          "Proposer signature invalid");
    }
  }

  private static boolean verify_slot(BeaconState state, BeaconBlock block) {
    // Verify that block.slot == state.slot
    return state.getSlot().compareTo(UnsignedLong.valueOf(block.getSlot())) == 0;
  }

  /**
   * @param state
   * @param block
   */
  public static void process_randao(BeaconState state, BeaconBlock block) {
    Validator proposer =
        state.getValidator_registry().get(get_beacon_proposer_index(state, state.getSlot()));

    checkArgument(
        bls_verify(
            proposer.getPubkey(),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(get_current_epoch(state).longValue())),
            block.getBody().getRandao_reveal(),
            get_domain(state.getFork(), get_current_epoch(state), DOMAIN_RANDAO)),
        "Provided randao value is invalid");

    // Mix Randao value in
    int index =
        get_current_epoch(state)
            .mod(UnsignedLong.valueOf(Constants.LATEST_RANDAO_MIXES_LENGTH))
            .intValue();
    Bytes32 newRandaoMix =
        get_randao_mix(state, get_current_epoch(state))
            .xor(Hash.keccak256(block.getBody().getRandao_reveal().toBytes()));
    state.getLatest_randao_mixes().set(index, newRandaoMix);
  }

  /**
   * @param state
   * @param block
   */
  public static void process_eth1_data(BeaconState state, BeaconBlock block) {
    for (Eth1DataVote eth1DataVote : state.getEth1_data_votes()) {
      // If someone else has already voted for the same hash, add to its counter
      if (eth1DataVote.getEth1_data().equals(block.getBody().getEth1_data())) {
        eth1DataVote.setVote_count(eth1DataVote.getVote_count().plus(UnsignedLong.ONE));
        return;
      }
    }

    // If we're seeing this hash for the first time, make a new counter
    state
        .getEth1_data_votes()
        .add(new Eth1DataVote(block.getBody().getEth1_data(), UnsignedLong.ONE));
  }

  /**
   * Process ``ProposerSlashing`` transaction. Note that this function mutates ``state``.
   *
   * @param state
   * @param block
   * @throws BlockProcessingException
   */
  public static void process_proposer_slashings(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    try {
      // Verify that len(block.body.proposer_slashings) <= MAX_PROPOSER_SLASHINGS
      checkArgument(
          block.getBody().getProposer_slashings().size() <= MAX_PROPOSER_SLASHINGS,
          "Proposer slashings more than limit in process_proposer_slashings()");

      // For each proposer_slashing in block.body.proposer_slashings:
      for (ProposerSlashing proposer_slashing : block.getBody().getProposer_slashings()) {
        // - Let proposer = state.validator_registry[proposer_slashing.proposer_index]
        Validator proposer =
            state
                .getValidator_registry()
                .get(toIntExact(proposer_slashing.getProposer_index().longValue()));

        // Verify that the epoch is the same
        checkArgument(
            slot_to_epoch(proposer_slashing.getHeader_1().getSlot())
                .equals(slot_to_epoch(proposer_slashing.getHeader_2().getSlot())),
            "Epoch is not the same in process_proposer_slashings");

        // But the headers are different
        checkArgument(
            !Objects.equals(
                proposer_slashing.getHeader_1().hash_tree_root(),
                proposer_slashing.getHeader_2().hash_tree_root()),
            "Headers are the same in process_proposer_slashings");

        // Proposer is not yet slashed
        checkArgument(
            !proposer.isSlashed(), "Proposer is already slashed in process_proposer_slashings");

        // Signatures are valid
        checkArgument(
            bls_verify(
                proposer.getPubkey(),
                proposer_slashing.getHeader_1().hash_tree_root(),
                proposer_slashing.getHeader_1().getSignature(),
                get_domain(
                    state.getFork(),
                    slot_to_epoch(proposer_slashing.getHeader_1().getSlot()),
                    DOMAIN_BEACON_BLOCK)),
            "BLSVerify fail for proposal header 1");

        checkArgument(
            bls_verify(
                proposer.getPubkey(),
                proposer_slashing.getHeader_2().hash_tree_root(),
                proposer_slashing.getHeader_2().getSignature(),
                get_domain(
                    state.getFork(),
                    slot_to_epoch(proposer_slashing.getHeader_2().getSlot()),
                    DOMAIN_BEACON_BLOCK)),
            "BLSVerify fail for proposal header 2");

        slash_validator(state, proposer_slashing.getProposer_index().intValue());
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in proposer_slashing()");
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Process ``AttesterSlashing`` transaction. Note that this function mutates ``state``.
   *
   * @param state
   * @param block
   * @see <a href=
   *     "https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#attester-slashings-1">spec</a>
   */
  // TODO: Parameter needs to be changed from BeaconBlock block to AttesterSlashing
  // attester_slashing.
  public static void process_attester_slashings(BeaconState state, BeaconBlock block)
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

        checkArgument(slashable_indices.size() >= 1, "Could not find slashable indices");
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
   * Process ``Attestation`` transaction. Note that this function mutates ``state``.
   *
   * @param state
   * @param block
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#attestations-1">spec</a>
   */
  public static void process_attestations(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    try {
      // Verify that len(block.body.attestations) <= MAX_ATTESTATIONS
      checkArgument(
          block.getBody().getAttestations().size() <= MAX_ATTESTATIONS,
          "Number of attestations more than limit in processAttestations()");

      for (Attestation attestation : block.getBody().getAttestations()) {
        UnsignedLong attestationDataSlot = attestation.getData().getSlot();
        checkArgument(
            attestationDataSlot.compareTo(UnsignedLong.valueOf(Constants.GENESIS_EPOCH)) >= 0,
            "Attestation in pre-history");
        checkArgument(
            state
                    .getSlot()
                    .compareTo(attestationDataSlot.plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH)))
                <= 0,
            "Attestation submitted too far in history");
        checkArgument(
            attestationDataSlot
                    .plus(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY))
                    .compareTo(state.getSlot())
                <= 0,
            "Attestation submitted too quickly");

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
        Crosslink latest_crosslink =
            state
                .getLatest_crosslinks()
                .get(toIntExact(attestation.getData().getShard().longValue()));
        checkArgument(
            latest_crosslink.equals(attestation.getData().getPrevious_crosslink())
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

        if (slot_to_epoch(attestation.getData().getSlot()).compareTo(get_current_epoch(state))
            == 0) {
          state.getCurrent_epoch_attestations().add(pendingAttestation);
        } else if (slot_to_epoch(attestation.getData().getSlot())
                .compareTo(get_previous_epoch(state))
            == 0) {
          state.getCurrent_epoch_attestations().add(pendingAttestation);
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in processAttestations()");
      throw new BlockProcessingException(e);
    }
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
            state, attestation.getData(), attestation.getAggregation_bitfield());
    List<Integer> custody_bit_1_participants =
        get_attestation_participants(
            state, attestation.getData(), attestation.getCustody_bitfield());
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
                new AttestationDataAndCustodyBit(attestation.getData(), false).hash_tree_root(),
                new AttestationDataAndCustodyBit(attestation.getData(), true).hash_tree_root()),
            attestation.getAggregate_signature(),
            get_domain(
                state.getFork(),
                slot_to_epoch(attestation.getData().getSlot()),
                DOMAIN_ATTESTATION)),
        "checkArgument threw and exception in verify_bitfields_and_aggregate_signature()");

    return true;
  }

  public static void process_deposits(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    try {
      // Verify that len(block.body.deposits) <= MAX_DEPOSITS
      checkArgument(
          block.getBody().getDeposits().size() <= MAX_DEPOSITS,
          "More deposits than the limit in process_deposits()");

      // SPEC TODO: add logic to ensure that deposits from 1.0 chain are processed in order

      // For each deposit in block.body.deposits:
      for (Deposit deposit : block.getBody().getDeposits()) {
        BeaconStateUtil.process_deposit(state, deposit);
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in processExits()");
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Process ``VoluntaryExit`` transaction. Note that this function mutates ``state``.
   *
   * @param state
   * @param block
   * @throws BlockProcessingException
   */
  // TODO: Parameter BeaconBlock block needs to be updated to VoluntaryExit exit.
  public static void process_voluntary_exits(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    try {
      // Verify that len(block.body.voluntary_exits) <= MAX_VOLUNTARY_EXITS
      checkArgument(
          block.getBody().getVoluntary_exits().size() <= Constants.MAX_VOLUNTARY_EXITS,
          "checkArgument threw and exception in processExits()");

      // For each exit in block.body.voluntaryExits:
      for (VoluntaryExit voluntaryExit : block.getBody().getVoluntary_exits()) {

        Validator validator =
            state
                .getValidator_registry()
                .get(toIntExact(voluntaryExit.getValidator_index().longValue()));

        // Verify the validator has not yet exited
        checkArgument(
            validator.getExit_epoch().compareTo(FAR_FUTURE_EPOCH) == 0, "Validator has exited");

        // Verify the validator has not initiated an exit
        checkArgument(!validator.hasInitiatedExit(), "Validator has initiated exit already");

        // Exits must specify an epoch when they become valid; they are not valid before then
        checkArgument(
            get_current_epoch(state).compareTo(voluntaryExit.getEpoch()) >= 0,
            "Exit is not valid yet");

        // Must have been in the validator set long enough
        checkArgument(
            get_current_epoch(state)
                    .minus(voluntaryExit.getEpoch())
                    .compareTo(UnsignedLong.valueOf(PERSISTENT_COMMITTEE_PERIOD))
                >= 0,
            "Validator was not in validator set long enough");

        // Verify signature
        checkArgument(
            bls_verify(
                validator.getPubkey(),
                voluntaryExit.signed_root("signature"),
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
   * Process ``Transfer`` transaction. Note that this function mutates ``state``.
   *
   * @param state
   * @param block
   * @throws BlockProcessingException
   */
  // TODO: Parameter BeaconBlock block needs to be updated to Transfer transfer.
  public static void process_transfers(BeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    // Verify that len(block.body.transfers) <= MAX_TRANSFERS and that all transfers are distinct.
    checkArgument(
        block.getBody().getTransfers().size() <= MAX_TRANSFERS,
        "More transfers in block than the limit");
    checkArgument(allDistinct(block.getBody().getTransfers()), "Transfers are not distinct");

    // For each transfer in block.body.transfers:
    for (Transfer transfer : block.getBody().getTransfers()) {
      // Verify the amount and fee aren't individually too big (for anti-overflow purposes)
      checkArgument(
          state
                  .getValidator_balances()
                  .get(transfer.getSender().intValue())
                  .compareTo(max(transfer.getAmount(), transfer.getFee()))
              >= 0,
          "Amount or fee too big");

      // Verify that we have enough ETH to send, and that after the transfer the balance will be
      // either
      //  exactly zero or at least MIN_DEPOSIT_AMOUNT
      checkArgument(
          state
                      .getValidator_balances()
                      .get(transfer.getSender().intValue())
                      .compareTo(transfer.getAmount().plus(transfer.getFee()))
                  == 0
              || state
                      .getValidator_balances()
                      .get(transfer.getSender().intValue())
                      .compareTo(
                          transfer
                              .getAmount()
                              .plus(transfer.getFee())
                              .plus(UnsignedLong.valueOf(MIN_DEPOSIT_AMOUNT)))
                  >= 0,
          "Not enought ETH to send deposit");

      // A transfer is valid in only one slot
      checkArgument(state.getSlot().equals(transfer.getSlot()), "Not the transfer slot");

      // Only withdrawn or not-yet-deposited accounts can transfer
      checkArgument(
          get_current_epoch(state)
                      .compareTo(
                          state
                              .getValidator_registry()
                              .get(transfer.getSender().intValue())
                              .getWithdrawable_epoch())
                  >= 0
              || state
                  .getValidator_registry()
                  .get(transfer.getSender().intValue())
                  .getActivation_epoch()
                  .equals(FAR_FUTURE_EPOCH),
          "Account neither withdrawn nor not-yet-deposited");

      // Verify that the pubkey is valid
      checkArgument(
          state
              .getValidator_registry()
              .get(transfer.getSender().intValue())
              .getWithdrawal_credentials()
              .equals(
                  Bytes.concatenate(
                      BLS_WITHDRAWAL_PREFIX_BYTE, transfer.getPubkey().toBytes().slice(1))),
          "Pubkey is not valid");

      // Verify that the signature is valid
      checkArgument(
          bls_verify(
              transfer.getPubkey(),
              transfer.signed_root("signature"),
              transfer.getSignature(),
              get_domain(state.getFork(), slot_to_epoch(transfer.getSlot()), DOMAIN_TRANSFER)),
          "Transfer signature invalid");

      // Process the transfer
      UnsignedLong senderBalance =
          state.getValidator_balances().get(transfer.getSender().intValue());
      senderBalance = senderBalance.minus(transfer.getAmount()).minus(transfer.getFee());
      state
          .getValidator_balances()
          .set(toIntExact(transfer.getSender().longValue()), senderBalance);

      UnsignedLong recipientBalance =
          state.getValidator_balances().get(transfer.getSender().intValue());
      recipientBalance = recipientBalance.plus(transfer.getAmount());
      state
          .getValidator_balances()
          .set(toIntExact(transfer.getRecipient().longValue()), recipientBalance);

      UnsignedLong proposerBalance =
          state.getValidator_balances().get(get_beacon_proposer_index(state, state.getSlot()));
      proposerBalance = proposerBalance.plus(transfer.getFee());
      state
          .getValidator_balances()
          .set(get_beacon_proposer_index(state, state.getSlot()), proposerBalance);
    }
  }

  /**
   * @param state
   * @param block
   */
  public static void verify_block_state_root(BeaconState state, BeaconBlock block) {
    if (!block.getState_root().equals(Bytes32.ZERO)) {
      checkArgument(
          block.getState_root().equals(state.hash_tree_root()),
          "State roots don't match in verify_block_state_root");
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
}

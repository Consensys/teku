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
import static tech.pegasys.artemis.datastructures.Constants.BLS_WITHDRAWAL_PREFIX;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_BEACON_PROPOSER;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_RANDAO;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_TRANSFER;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_VOLUNTARY_EXIT;
import static tech.pegasys.artemis.datastructures.Constants.EPOCHS_PER_HISTORICAL_VECTOR;
import static tech.pegasys.artemis.datastructures.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSITS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.artemis.datastructures.Constants.MAX_EPOCHS_PER_CROSSLINK;
import static tech.pegasys.artemis.datastructures.Constants.MIN_DEPOSIT_AMOUNT;
import static tech.pegasys.artemis.datastructures.Constants.PERSISTENT_COMMITTEE_PERIOD;
import static tech.pegasys.artemis.datastructures.Constants.SHARD_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_attestation_data_slot;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_indexed_attestation;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.is_slashable_attestation_data;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_of_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initiate_validator_exit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.max;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.min;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.process_deposit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.slash_validator;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_crosslink_committee;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.decrease_balance;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.increase_balance;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.is_active_validator;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.is_slashable_validator;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;

import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public final class BlockProcessorUtil {

  private static final ALogger STDOUT = new ALogger("stdout");

  /**
   * Processes block header
   *
   * @param state
   * @param block
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#block-header</a>
   */
  public static void process_block_header(
      BeaconState state, BeaconBlock block, boolean validate_state_root)
      throws BlockProcessingException {
    try {
      checkArgument(
          block.getSlot().equals(state.getSlot()),
          "process_block_header: Verify that the slots match");
      checkArgument(
          block.getParent_root().equals(state.getLatest_block_header().signing_root("signature")),
          "process_block_header: Verify that the parent matches");

      // Save the current block as the new latest block
      state.setLatest_block_header(
          new BeaconBlockHeader(
              block.getSlot(),
              block.getParent_root(),
              Bytes32.ZERO, // Overwritten in the next `process_slot` call
              block.getBody().hash_tree_root(),
              BLSSignature.empty()));

      // Only if we are processing blocks (not proposing them)
      Validator proposer = state.getValidators().get(get_beacon_proposer_index(state));
      checkArgument(!proposer.isSlashed(), "process_block_header: Verify proposer is not slashed");
      if (validate_state_root) {
        checkArgument(
            bls_verify(
                proposer.getPubkey(),
                block.signing_root("signature"),
                block.getSignature(),
                get_domain(state, DOMAIN_BEACON_PROPOSER)),
            "process_block_header: Verify proposer signature");
      }
    } catch (IllegalArgumentException e) {
      STDOUT.log(Level.WARN, e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Processes randao
   *
   * @param state
   * @param body
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#randao</a>
   */
  public static void process_randao(BeaconState state, BeaconBlockBody body)
      throws BlockProcessingException {
    try {
      UnsignedLong epoch = get_current_epoch(state);
      // Verify RANDAO reveal
      int proposer_index = get_beacon_proposer_index(state);
      Validator proposer = state.getValidators().get(proposer_index);
      Bytes32 messageHash =
          HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(epoch.longValue()));
      checkArgument(
          bls_verify(
              proposer.getPubkey(),
              messageHash,
              body.getRandao_reveal(),
              get_domain(state, DOMAIN_RANDAO)),
          "process_randao: Verify that the provided randao value is valid");
      // Mix in RANDAO reveal
      Bytes32 mix =
          get_randao_mix(state, epoch).xor(Hash.sha2_256(body.getRandao_reveal().toBytes()));
      int index = epoch.mod(UnsignedLong.valueOf(EPOCHS_PER_HISTORICAL_VECTOR)).intValue();
      state.getRandao_mixes().set(index, mix);
    } catch (IllegalArgumentException e) {
      STDOUT.log(Level.WARN, e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Processes Eth1 data
   *
   * @param state
   * @param body
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#eth1-data</a>
   */
  public static void process_eth1_data(BeaconState state, BeaconBlockBody body) {
    state.getEth1_data_votes().add(body.getEth1_data());
    long vote_count =
        state.getEth1_data_votes().stream()
            .filter(item -> item.equals(body.getEth1_data()))
            .count();
    if (vote_count * 2 > SLOTS_PER_ETH1_VOTING_PERIOD) {
      state.setEth1_data(body.getEth1_data());
    }
  }

  /**
   * Processes all block body operations
   *
   * @param state
   * @param body
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#operations</a>
   */
  public static void process_operations(BeaconState state, BeaconBlockBody body)
      throws BlockProcessingException {
    try {

      checkArgument(
          body.getDeposits().size()
              == Math.min(
                  MAX_DEPOSITS,
                  toIntExact(
                      state
                          .getEth1_data()
                          .getDeposit_count()
                          .minus(state.getEth1_deposit_index())
                          .longValue())),
          "process_operations: Verify that outstanding deposits are processed up to the maximum number of deposits");
      Set<Transfer> transfersSet = new HashSet<>(body.getTransfers());
      checkArgument(
          body.getTransfers().size() == transfersSet.size(),
          "process_operations: Verify that there are no duplicate transfers");

      process_proposer_slashings(state, body.getProposer_slashings());
      process_attester_slashings(state, body.getAttester_slashings());
      process_attestations(state, body.getAttestations());
      process_deposits(state, body.getDeposits());
      process_voluntary_exits(state, body.getVoluntary_exits());
      process_transfers(state, body.getTransfers());

    } catch (IllegalArgumentException e) {
      STDOUT.log(Level.WARN, e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Processes proposer slashings
   *
   * @param state
   * @param proposerSlashings
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#proposer-slashings</a>
   */
  public static void process_proposer_slashings(
      BeaconState state, List<ProposerSlashing> proposerSlashings) throws BlockProcessingException {
    try {
      // For each proposer_slashing in block.body.proposer_slashings:
      for (ProposerSlashing proposer_slashing : proposerSlashings) {
        checkArgument(
            UnsignedLong.valueOf(state.getValidators().size())
                    .compareTo(proposer_slashing.getProposer_index())
                > 0,
            "process_proposer_slashings: Invalid proposer index");
        Validator proposer =
            state
                .getValidators()
                .get(toIntExact(proposer_slashing.getProposer_index().longValue()));

        checkArgument(
            compute_epoch_of_slot(proposer_slashing.getHeader_1().getSlot())
                .equals(compute_epoch_of_slot(proposer_slashing.getHeader_2().getSlot())),
            "process_proposer_slashings: Verify that the epoch is the same");

        checkArgument(
            !Objects.equals(
                proposer_slashing.getHeader_1().hash_tree_root(),
                proposer_slashing.getHeader_2().hash_tree_root()),
            "process_proposer_slashings: Verify that the headers are different");

        checkArgument(
            is_slashable_validator(proposer, get_current_epoch(state)),
            "process_proposer_slashings: Check proposer is slashable");

        checkArgument(
            bls_verify(
                proposer.getPubkey(),
                proposer_slashing.getHeader_1().signing_root("signature"),
                proposer_slashing.getHeader_1().getSignature(),
                get_domain(
                    state,
                    DOMAIN_BEACON_PROPOSER,
                    compute_epoch_of_slot(proposer_slashing.getHeader_1().getSlot()))),
            "process_proposer_slashings: Verify signatures are valid 1");

        checkArgument(
            bls_verify(
                proposer.getPubkey(),
                proposer_slashing.getHeader_2().signing_root("signature"),
                proposer_slashing.getHeader_2().getSignature(),
                get_domain(
                    state,
                    DOMAIN_BEACON_PROPOSER,
                    compute_epoch_of_slot(proposer_slashing.getHeader_2().getSlot()))),
            "process_proposer_slashings: Verify signatures are valid 2");

        slash_validator(state, toIntExact(proposer_slashing.getProposer_index().longValue()));
      }
    } catch (IllegalArgumentException e) {
      STDOUT.log(Level.WARN, e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Processes attester slashings
   *
   * @param state
   * @param attesterSlashings
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#attester-slashings</a>
   */
  public static void process_attester_slashings(
      BeaconState state, List<AttesterSlashing> attesterSlashings) throws BlockProcessingException {
    try {

      // For each attester_slashing in block.body.attester_slashings:
      for (AttesterSlashing attester_slashing : attesterSlashings) {
        IndexedAttestation attestation_1 = attester_slashing.getAttestation_1();
        IndexedAttestation attestation_2 = attester_slashing.getAttestation_2();

        checkArgument(
            is_slashable_attestation_data(attestation_1.getData(), attestation_2.getData()),
            "process_attester_slashings: Verify if attestations are slashable");

        checkArgument(
            is_valid_indexed_attestation(state, attestation_1),
            "process_attester_slashings: Is valid indexed attestation 1");
        checkArgument(
            is_valid_indexed_attestation(state, attestation_2),
            "process_attester_slashings: Is valid indexed attestation 2");
        boolean slashed_any = false;

        List<UnsignedLong> attesting_indices_1 = attestation_1.getCustody_bit_0_indices();
        attesting_indices_1.addAll(attestation_1.getCustody_bit_1_indices());
        List<UnsignedLong> attesting_indices_2 = attestation_2.getCustody_bit_0_indices();
        attesting_indices_2.addAll(attestation_1.getCustody_bit_1_indices());

        // retainAll is being used to get the intersection of these two lists
        List<UnsignedLong> sorted_intersection_of_indices = attesting_indices_1;
        sorted_intersection_of_indices.retainAll(attesting_indices_2);
        Collections.sort(sorted_intersection_of_indices);

        for (UnsignedLong index : sorted_intersection_of_indices) {
          if (is_slashable_validator(
              state.getValidators().get(toIntExact(index.longValue())), get_current_epoch(state))) {
            slash_validator(state, toIntExact(index.longValue()));
            slashed_any = true;
          }
        }

        checkArgument(slashed_any, "process_attester_slashings: No one is slashed");
      }
    } catch (IllegalArgumentException e) {
      STDOUT.log(Level.WARN, e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Processes attestations
   *
   * @param state
   * @param attestations
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#attestations</a>
   */
  public static void process_attestations(BeaconState state, List<Attestation> attestations)
      throws BlockProcessingException {
    try {

      for (Attestation attestation : attestations) {
        AttestationData data = attestation.getData();
        checkArgument(
            data.getCrosslink().getShard().longValue() < SHARD_COUNT,
            "process_attestations: Crosslink shard value too high");
        checkArgument(
            data.getTarget().getEpoch().equals(get_previous_epoch(state))
                || data.getTarget().getEpoch().equals(get_current_epoch(state)),
            "process_attestations: Attestation not from current or previous epoch");

        UnsignedLong attestation_slot = get_attestation_data_slot(state, data);
        checkArgument(
            attestation_slot
                    .plus(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY))
                    .compareTo(state.getSlot())
                <= 0,
            "process_attestations: Attestation submitted too quickly");

        checkArgument(
            state.getSlot().compareTo(attestation_slot.plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH)))
                <= 0,
            "process_attestations: Attestation submitted too far in history");

        List<Integer> committee =
            get_crosslink_committee(
                state, data.getTarget().getEpoch(), data.getCrosslink().getShard());
        checkArgument(
            attestation.getAggregation_bits().getByteArray().length
                    == attestation.getCustody_bitfield().getByteArray().length
                && attestation.getAggregation_bits().getByteArray().length == committee.size(),
            "process_attestations: Attestation aggregation bit, custody bit, and committee doesn't have the same length");

        PendingAttestation pendingAttestation =
            new PendingAttestation(
                attestation.getAggregation_bits(),
                data,
                state.getSlot().minus(attestation_slot),
                UnsignedLong.valueOf(get_beacon_proposer_index(state)));

        Crosslink parent_crosslink;
        if (data.getTarget().getEpoch().equals(get_current_epoch(state))) {
          checkArgument(
              data.getSource().equals(state.getCurrent_justified_checkpoint()),
              "process_attestations: Attestation source error 1");
          parent_crosslink =
              state.getCurrent_crosslinks().get(data.getCrosslink().getShard().intValue());
          state.getCurrent_epoch_attestations().add(pendingAttestation);
        } else {
          checkArgument(
              data.getSource().equals(state.getPrevious_justified_checkpoint()),
              "process_attestations: Attestation source error 2");
          parent_crosslink =
              state.getPrevious_crosslinks().get(data.getCrosslink().getShard().intValue());
          state.getPrevious_epoch_attestations().add(pendingAttestation);
        }

        // Check crosslink against expected parent crosslink
        checkArgument(
            data.getCrosslink().getParent_root().equals(parent_crosslink.hash_tree_root()),
            "process_attestations: Check crosslink data 1");
        checkArgument(
            data.getCrosslink().getStart_epoch().equals(parent_crosslink.getEnd_epoch()),
            "process_attestations: Check crosslink data 2");
        checkArgument(
            data.getCrosslink()
                .getEnd_epoch()
                .equals(
                    min(
                        data.getTarget().getEpoch(),
                        parent_crosslink
                            .getEnd_epoch()
                            .plus(UnsignedLong.valueOf(MAX_EPOCHS_PER_CROSSLINK)))),
            "process_attestations: Check crosslink data 3");
        checkArgument(
            data.getCrosslink().getData_root().equals(ZERO_HASH),
            "process_attestations: Check crosslink data 4");

        checkArgument(
            is_valid_indexed_attestation(state, get_indexed_attestation(state, attestation)),
            "process_attestations: Check signature");
      }
    } catch (IllegalArgumentException e) {
      STDOUT.log(Level.WARN, e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Processes deposits
   *
   * @param state
   * @param deposits
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#deposits</a>
   */
  public static void process_deposits(BeaconState state, List<? extends Deposit> deposits)
      throws BlockProcessingException {
    try {
      for (Deposit deposit : deposits) {
        process_deposit(state, deposit);
      }
    } catch (IllegalArgumentException e) {
      STDOUT.log(Level.WARN, e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Processes voluntary exits
   *
   * @param state
   * @param exits
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#voluntary-exits</a>
   */
  public static void process_voluntary_exits(BeaconState state, List<VoluntaryExit> exits)
      throws BlockProcessingException {
    try {

      // For each exit in block.body.voluntaryExits:
      for (VoluntaryExit exit : exits) {

        checkArgument(
            UnsignedLong.valueOf(state.getValidators().size()).compareTo(exit.getValidator_index())
                > 0,
            "process_voluntary_exits: Invalid validator index");
        Validator validator =
            state.getValidators().get(toIntExact(exit.getValidator_index().longValue()));

        checkArgument(
            is_active_validator(validator, get_current_epoch(state)),
            "process_voluntary_exits: Verify the validator is active");

        checkArgument(
            validator.getExit_epoch().compareTo(FAR_FUTURE_EPOCH) == 0,
            "process_voluntary_exits: Verify the validator has not yet exited");

        checkArgument(
            get_current_epoch(state).compareTo(exit.getEpoch()) >= 0,
            "process_voluntary_exits: Exits must specify an epoch when they become valid; they are not valid before then");

        checkArgument(
            get_current_epoch(state)
                    .compareTo(
                        validator
                            .getActivation_epoch()
                            .plus(UnsignedLong.valueOf(PERSISTENT_COMMITTEE_PERIOD)))
                >= 0,
            "process_voluntary_exits: Verify the validator has been active long enough");

        Bytes domain = get_domain(state, DOMAIN_VOLUNTARY_EXIT, exit.getEpoch());
        checkArgument(
            bls_verify(
                validator.getPubkey(), exit.signing_root("signature"), exit.getSignature(), domain),
            "process_voluntary_exits: Verify signature");

        // - Run initiate_validator_exit(state, exit.validator_index)
        initiate_validator_exit(state, toIntExact(exit.getValidator_index().longValue()));
      }
    } catch (IllegalArgumentException e) {
      STDOUT.log(Level.WARN, e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Processes transfers
   *
   * @param state
   * @param transfers
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#transfers</a>
   */
  public static void process_transfers(BeaconState state, List<Transfer> transfers)
      throws BlockProcessingException {
    try {

      for (Transfer transfer : transfers) {
        checkArgument(
            state
                    .getBalances()
                    .get(transfer.getSender().intValue())
                    .compareTo(
                        max(
                            max(transfer.getAmount().plus(transfer.getFee()), transfer.getAmount()),
                            transfer.getFee()))
                >= 0,
            "process_transfers: Verify the balance the covers amount and fee (with overflow protection)");

        checkArgument(
            state.getSlot().equals(transfer.getSlot()),
            "process_tranfers: A transfer is valid in only one slot");

        checkArgument(
            state
                    .getValidators()
                    .get(transfer.getSender().intValue())
                    .getActivation_eligibility_epoch()
                    .equals(FAR_FUTURE_EPOCH)
                || get_current_epoch(state)
                        .compareTo(
                            state
                                .getValidators()
                                .get(transfer.getSender().intValue())
                                .getWithdrawable_epoch())
                    >= 0
                || state
                        .getBalances()
                        .get(transfer.getSender().intValue())
                        .compareTo(
                            transfer
                                .getAmount()
                                .plus(transfer.getFee())
                                .plus(UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE)))
                    >= 0,
            "process_tranfers: Sender must satisfy one of the conditions");

        checkArgument(
            state
                .getValidators()
                .get(transfer.getSender().intValue())
                .getWithdrawal_credentials()
                .equals(
                    Bytes.wrap(
                        BLS_WITHDRAWAL_PREFIX,
                        Hash.sha2_256(transfer.getPubkey().toBytes().slice(1)))),
            "process_tranfers: Verify that the pubkey is valid");

        checkArgument(
            bls_verify(
                transfer.getPubkey(),
                transfer.signing_root("signature"),
                transfer.getSignature(),
                get_domain(state, DOMAIN_TRANSFER)),
            "process_tranfers: Verify that the signature is valid");

        // Process transfers
        decrease_balance(
            state, transfer.getSender().intValue(), transfer.getAmount().plus(transfer.getFee()));
        increase_balance(state, transfer.getRecipient().intValue(), transfer.getAmount());
        increase_balance(state, get_beacon_proposer_index(state), transfer.getFee());

        checkArgument(
            !(UnsignedLong.ZERO.compareTo(state.getBalances().get(transfer.getSender().intValue()))
                    < 0
                && state
                        .getBalances()
                        .get(transfer.getSender().intValue())
                        .compareTo(UnsignedLong.valueOf(MIN_DEPOSIT_AMOUNT))
                    < 0),
            "process_tranfers: Verify balances are not dust 1");
        checkArgument(
            !(UnsignedLong.ZERO.compareTo(
                        state.getBalances().get(transfer.getRecipient().intValue()))
                    < 0
                && state
                        .getBalances()
                        .get(transfer.getSender().intValue())
                        .compareTo(UnsignedLong.valueOf(MIN_DEPOSIT_AMOUNT))
                    < 0),
            "process_tranfers: Verify balances are not dust 2");
      }
    } catch (IllegalArgumentException e) {
      STDOUT.log(Level.WARN, e.getMessage());
      throw new BlockProcessingException(e);
    }
  }
}

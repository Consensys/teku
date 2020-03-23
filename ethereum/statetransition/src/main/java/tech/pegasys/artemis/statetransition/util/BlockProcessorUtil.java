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
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.is_slashable_attestation_data;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_committee_count_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initiate_validator_exit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.process_deposit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.slash_validator;
import static tech.pegasys.artemis.datastructures.util.CommitteeUtil.get_beacon_committee;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.is_active_validator;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.is_slashable_validator;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_PROPOSER;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_VOLUNTARY_EXIT;
import static tech.pegasys.artemis.util.config.Constants.EPOCHS_PER_HISTORICAL_VECTOR;
import static tech.pegasys.artemis.util.config.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.MAX_DEPOSITS;
import static tech.pegasys.artemis.util.config.Constants.PERSISTENT_COMMITTEE_PERIOD;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;

import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedLong;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public final class BlockProcessorUtil {

  /**
   * Processes block header
   *
   * @param state
   * @param block
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#block-header</a>
   */
  public static void process_block_header(MutableBeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    try {
      checkArgument(
          block.getSlot().equals(state.getSlot()),
          "process_block_header: Verify that the slots match");
      checkArgument(
          block.getParent_root().equals(state.getLatest_block_header().hash_tree_root()),
          "process_block_header: Verify that the parent matches");

      // Save the current block as the new latest block
      state.setLatest_block_header(
          new BeaconBlockHeader(
              block.getSlot(),
              block.getParent_root(),
              Bytes32.ZERO, // Overwritten in the next `process_slot` call
              block.getBody().hash_tree_root()));

      // Only if we are processing blocks (not proposing them)
      Validator proposer = state.getValidators().get(get_beacon_proposer_index(state));
      checkArgument(!proposer.isSlashed(), "process_block_header: Verify proposer is not slashed");

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
  public static void process_randao(
      MutableBeaconState state, BeaconBlockBody body, boolean validateRandao)
      throws BlockProcessingException {
    try {
      UnsignedLong epoch = get_current_epoch(state);
      // Verify RANDAO reveal
      int proposer_index = get_beacon_proposer_index(state);
      Validator proposer = state.getValidators().get(proposer_index);
      Bytes32 messageHash =
          HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(epoch.longValue()));
      /* zzz */
      //      checkArgument(
      //          !validateRandao
      //              || bls_verify(
      //                  proposer.getPubkey(),
      //                  messageHash,
      //                  body.getRandao_reveal(),
      //                  get_domain(state, DOMAIN_RANDAO)),
      //          "process_randao: Verify that the provided randao value is valid");
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
  public static void process_eth1_data(MutableBeaconState state, BeaconBlockBody body) {
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
  public static void process_operations(MutableBeaconState state, BeaconBlockBody body)
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

      process_proposer_slashings(state, body.getProposer_slashings());
      process_attester_slashings(state, body.getAttester_slashings());
      process_attestations(state, body.getAttestations());
      process_deposits(state, body.getDeposits());
      process_voluntary_exits(state, body.getVoluntary_exits());
      // @process_shard_receipt_proofs
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
      MutableBeaconState state, SSZList<ProposerSlashing> proposerSlashings)
      throws BlockProcessingException {
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
            proposer_slashing
                .getHeader_1()
                .getMessage()
                .getSlot()
                .equals(proposer_slashing.getHeader_2().getMessage().getSlot()),
            "process_proposer_slashings: Verify that the slots match");

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
                proposer_slashing.getHeader_1().getMessage().hash_tree_root(),
                proposer_slashing.getHeader_1().getSignature(),
                get_domain(
                    state,
                    DOMAIN_BEACON_PROPOSER,
                    compute_epoch_at_slot(proposer_slashing.getHeader_1().getMessage().getSlot()))),
            "process_proposer_slashings: Verify signatures are valid 1");

        checkArgument(
            bls_verify(
                proposer.getPubkey(),
                proposer_slashing.getHeader_2().getMessage().hash_tree_root(),
                proposer_slashing.getHeader_2().getSignature(),
                get_domain(
                    state,
                    DOMAIN_BEACON_PROPOSER,
                    compute_epoch_at_slot(proposer_slashing.getHeader_2().getMessage().getSlot()))),
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
      MutableBeaconState state, SSZList<AttesterSlashing> attesterSlashings)
      throws BlockProcessingException {
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

        Set<UnsignedLong> indices =
            Sets.intersection(
                new TreeSet<>(
                    attestation_1.getAttesting_indices().asList()), // TreeSet as must be sorted
                new HashSet<>(attestation_2.getAttesting_indices().asList()));

        for (UnsignedLong index : indices) {
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
  public static void process_attestations(
      MutableBeaconState state, SSZList<Attestation> attestations) throws BlockProcessingException {
    try {

      for (Attestation attestation : attestations) {
        AttestationData data = attestation.getData();
        checkArgument(
            data.getIndex().compareTo(get_committee_count_at_slot(state, data.getSlot())) < 0,
            "process_attestations: CommitteeIndex too high");
        checkArgument(
            data.getTarget().getEpoch().equals(get_previous_epoch(state))
                || data.getTarget().getEpoch().equals(get_current_epoch(state)),
            "process_attestations: Attestation not from current or previous epoch");
        checkArgument(
            data.getTarget().getEpoch().equals(compute_epoch_at_slot(data.getSlot())),
            "process_attestations: Attestation slot not in specified epoch");
        checkArgument(
            data.getSlot()
                    .plus(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY))
                    .compareTo(state.getSlot())
                <= 0,
            "process_attestations: Attestation submitted too quickly");

        checkArgument(
            state.getSlot().compareTo(data.getSlot().plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH)))
                <= 0,
            "process_attestations: Attestation submitted too far in history");

        List<Integer> committee = get_beacon_committee(state, data.getSlot(), data.getIndex());
        checkArgument(
            attestation.getAggregation_bits().getByteArray().length == committee.size(),
            "process_attestations: Attestation aggregation bit, custody bit, and committee doesn't have the same length");

        PendingAttestation pendingAttestation =
            new PendingAttestation(
                attestation.getAggregation_bits(),
                data,
                state.getSlot().minus(data.getSlot()),
                UnsignedLong.valueOf(get_beacon_proposer_index(state)));

        if (data.getTarget().getEpoch().equals(get_current_epoch(state))) {
          checkArgument(
              data.getSource().equals(state.getCurrent_justified_checkpoint()),
              "process_attestations: Attestation source error 1");
          state.getCurrent_epoch_attestations().add(pendingAttestation);
        } else {
          checkArgument(
              data.getSource().equals(state.getPrevious_justified_checkpoint()),
              "process_attestations: Attestation source error 2");
          state.getPrevious_epoch_attestations().add(pendingAttestation);
        }
      }

      //      attestations.stream()
      //          .parallel()
      //          // zzz
      //          .filter(a -> !is_valid_indexed_attestation(state, get_indexed_attestation(state,
      // a)))
      //          .findAny()
      //          .ifPresent(
      //              invalidAttestation -> {
      //                throw new IllegalArgumentException(
      //                    "Invalid attestation signature: " + invalidAttestation);
      //              });
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
  public static void process_deposits(MutableBeaconState state, SSZList<? extends Deposit> deposits)
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
  public static void process_voluntary_exits(
      MutableBeaconState state, SSZList<SignedVoluntaryExit> exits)
      throws BlockProcessingException {
    try {

      // For each exit in block.body.voluntaryExits:
      for (SignedVoluntaryExit signedExit : exits) {
        final VoluntaryExit exit = signedExit.getMessage();
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
                validator.getPubkey(), exit.hash_tree_root(), signedExit.getSignature(), domain),
            "process_voluntary_exits: Verify signature");

        // - Run initiate_validator_exit(state, exit.validator_index)
        initiate_validator_exit(state, toIntExact(exit.getValidator_index().longValue()));
      }
    } catch (IllegalArgumentException e) {
      STDOUT.log(Level.WARN, e.getMessage());
      throw new BlockProcessingException(e);
    }
  }
}

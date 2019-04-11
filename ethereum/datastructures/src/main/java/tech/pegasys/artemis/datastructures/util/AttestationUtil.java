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

package tech.pegasys.artemis.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_attestation_participants;

import com.google.common.primitives.UnsignedLong;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.PriorityBlockingQueue;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public class AttestationUtil {

  private static final ALogger LOG = new ALogger(AttestationUtil.class.getName());
  /**
   * Returns the attestations specific for the specific epoch.
   *
   * @param state
   * @param epoch
   * @return
   */
  static List<PendingAttestation> get_epoch_attestations(BeaconState state, UnsignedLong epoch)
      throws IllegalArgumentException {
    List<PendingAttestation> latest_attestations = state.getLatest_attestations();
    List<PendingAttestation> epoch_attestations = new ArrayList<>();

    for (PendingAttestation attestation : latest_attestations) {
      if (epoch.equals(BeaconStateUtil.slot_to_epoch(attestation.getData().getSlot()))) {
        epoch_attestations.add(attestation);
      }
    }

    checkArgument(epoch_attestations.size() != 0, "There are no epoch_attestations");
    return epoch_attestations;
  }

  /**
   * Returns the current epoch boundary attestations.
   *
   * @param state
   * @return List<PendingAttestation>
   * @throws IllegalArgumentException
   */
  public static List<PendingAttestation> get_current_epoch_boundary_attestations(BeaconState state)
      throws IllegalArgumentException {

    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
    List<PendingAttestation> current_epoch_attestations =
        get_epoch_attestations(state, current_epoch);

    List<PendingAttestation> current_epoch_boundary_attestations = new ArrayList<>();

    for (PendingAttestation attestation : current_epoch_attestations) {
      if (attestation
              .getData()
              .getEpoch_boundary_root()
              .equals(
                  BeaconStateUtil.get_block_root(
                      state, BeaconStateUtil.get_epoch_start_slot(current_epoch)))
          && attestation.getData().getJustified_epoch().equals(state.getJustified_epoch())) {
        current_epoch_boundary_attestations.add(attestation);
      }
    }
    checkArgument(
        current_epoch_boundary_attestations.size() != 0,
        "There are no current_epoch_boundary_attestations");
    return current_epoch_boundary_attestations;
  }

  /**
   * Returns the previous epoch boundary attestations.
   *
   * @param state
   * @return List<PendingAttestation>
   * @throws IllegalArgumentException
   */
  public static List<PendingAttestation> get_previous_epoch_boundary_attestations(BeaconState state)
      throws IllegalArgumentException {

    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);
    List<PendingAttestation> previous_epoch_attestations =
        get_epoch_attestations(state, previous_epoch);

    List<PendingAttestation> previous_epoch_boundary_attestations = new ArrayList<>();

    for (PendingAttestation attestation : previous_epoch_attestations) {
      if (attestation
          .getData()
          .getEpoch_boundary_root()
          .equals(
              BeaconStateUtil.get_block_root(
                  state, BeaconStateUtil.get_epoch_start_slot(previous_epoch)))) {
        previous_epoch_boundary_attestations.add(attestation);
      }
    }
    checkArgument(
        previous_epoch_boundary_attestations.size() != 0,
        "There are no previous_epoch_boundary_attestations");
    return previous_epoch_boundary_attestations;
  }

  /**
   * Returns the previous epoch justified attestations.
   *
   * @param state
   * @return List<PendingAttestation>
   * @throws IllegalArgumentException
   */
  public static List<PendingAttestation> get_previous_epoch_justified_attestations(
      BeaconState state) throws IllegalArgumentException {
    // Get previous and current epoch
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);

    // Get previous and current_epoch_attestations
    List<PendingAttestation> attestations = get_epoch_attestations(state, previous_epoch);

    attestations.addAll(get_epoch_attestations(state, current_epoch));

    UnsignedLong justified_epoch = state.getJustified_epoch();
    List<PendingAttestation> previous_epoch_justified_attestations = new ArrayList<>();
    for (PendingAttestation attestation : attestations) {
      if (attestation.getData().getJustified_epoch().equals(justified_epoch)) {
        previous_epoch_justified_attestations.add(attestation);
      }
    }

    return previous_epoch_justified_attestations;
  }

  /**
   * Returns the previous epoch justified attestation indices.
   *
   * @param state
   * @return List<Integer>
   * @throws IllegalArgumentException
   */
  public static List<Integer> get_previous_epoch_justified_attester_indices(BeaconState state)
      throws IllegalArgumentException {
    // Get previous_epoch_justified_attestations
    List<PendingAttestation> previous_epoch_justified_attestations =
        get_previous_epoch_justified_attestations(state);

    return get_attester_indices(state, previous_epoch_justified_attestations);
  }

  /**
   * Returns the previous epoch justified attesting balance.
   *
   * @param state
   * @return UnsignedLong
   * @throws IllegalArgumentException
   */
  public static UnsignedLong get_previous_epoch_justified_attesting_balance(BeaconState state)
      throws IllegalArgumentException {
    // Get previous_epoch_justified_attester_indices
    List<Integer> previous_epoch_justified_attester_indices =
        get_previous_epoch_justified_attester_indices(state);

    return get_total_attesting_balance(state, previous_epoch_justified_attester_indices);
  }

  /**
   * Returns the previous epoch boundary attester indices.
   *
   * @param state
   * @return List<Integer>
   * @throws IllegalArgumentException
   */
  public static List<Integer> get_previous_epoch_boundary_attester_indices(BeaconState state)
      throws IllegalArgumentException {

    // Get previous_epoch_boundary_attestations
    List<PendingAttestation> previous_epoch_boundary_attestations =
        get_previous_epoch_boundary_attestations(state);

    return get_attester_indices(state, previous_epoch_boundary_attestations);
  }

  /**
   * Returns the sum of balances for all the attesters that were active at the current epoch
   * boundary
   *
   * @param state
   * @return UnsignedLong
   * @throws IllegalArgumentException
   */
  public static UnsignedLong get_current_epoch_boundary_attesting_balance(BeaconState state)
      throws IllegalArgumentException {

    // Get current epoch_boundary_attestations
    List<PendingAttestation> current_epoch_boundary_attestations =
        get_current_epoch_boundary_attestations(state);

    // Get current_epoch_boundary_attester_indices
    List<Integer> current_epoch_boundary_attester_indices =
        get_attester_indices(state, current_epoch_boundary_attestations);

    return get_total_attesting_balance(state, current_epoch_boundary_attester_indices);
  }

  /**
   * Returns the sum of balances for all the attesters that were active at the previous epoch
   * boundary
   *
   * @param state
   * @return previous_epoch_boundary_attesting_balance
   * @throws IllegalArgumentException
   */
  public static UnsignedLong get_previous_epoch_boundary_attesting_balance(BeaconState state)
      throws IllegalArgumentException {

    List<Integer> previous_epoch_boundary_attester_indices =
        get_previous_epoch_boundary_attester_indices(state);

    return get_total_attesting_balance(state, previous_epoch_boundary_attester_indices);
  }

  /**
   * Returns the previous epoch head attestations
   *
   * @param state
   * @return List<PendingAttestation>
   * @throws IllegalArgumentException
   */
  public static List<PendingAttestation> get_previous_epoch_head_attestations(BeaconState state)
      throws IllegalArgumentException {
    // Get previous epoch
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);

    // Get current_epoch_attestations
    List<PendingAttestation> previous_epoch_attestations =
        get_epoch_attestations(state, previous_epoch);

    List<PendingAttestation> previous_epoch_head_attestations = new ArrayList<>();
    for (PendingAttestation attestation : previous_epoch_attestations) {
      if (attestation
          .getData()
          .getBeacon_block_root()
          .equals(BeaconStateUtil.get_block_root(state, attestation.getData().getSlot()))) {
        previous_epoch_head_attestations.add(attestation);
      }
    }
    return previous_epoch_head_attestations;
  }

  /**
   * Returns the previous epoch head attestor indices
   *
   * @param state
   * @return List<Integer>
   * @throws IllegalArgumentException
   */
  public static List<Integer> get_previous_epoch_head_attester_indices(BeaconState state)
      throws IllegalArgumentException {
    List<PendingAttestation> previous_epoch_head_attestations =
        get_previous_epoch_head_attestations(state);

    return get_attester_indices(state, previous_epoch_head_attestations);
  }

  /**
   * Returns the previous epoch head attesting balance
   *
   * @param state
   * @return UnsignedLong
   * @throws IllegalArgumentException
   */
  public static UnsignedLong get_previous_epoch_head_attesting_balance(BeaconState state)
      throws IllegalArgumentException {
    List<Integer> previous_epoch_head_attester_indices =
        get_previous_epoch_head_attester_indices(state);

    return get_total_attesting_balance(state, previous_epoch_head_attester_indices);
  }

  /**
   * Returns the previous epoch attester indices
   *
   * @param state
   * @return List<Integer>
   * @throws IllegalArgumentException
   */
  public static List<Integer> get_previous_epoch_attester_indices(BeaconState state)
      throws IllegalArgumentException {
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);

    List<PendingAttestation> previous_epoch_attestations =
        get_epoch_attestations(state, previous_epoch);

    return get_attester_indices(state, previous_epoch_attestations);
  }

  /**
   * Returns the previous epoch attesting balance
   *
   * @param state
   * @return UnsignedLong
   * @throws IllegalArgumentException
   */
  public static UnsignedLong get_previous_epoch_attesting_balance(BeaconState state)
      throws IllegalArgumentException {
    List<Integer> previous_epoch_attester_indices = get_previous_epoch_attester_indices(state);

    return get_total_attesting_balance(state, previous_epoch_attester_indices);
  }

  /**
   * Returns the union of validator index sets, where the sets are the attestation participants of
   * attestations passed in TODO: the union part takes O(n^2) time, where n is the number of
   * validators. OPTIMIZE
   *
   * @param state
   * @param attestations
   * @return attester_indices
   * @throws IllegalArgumentException
   */
  static List<Integer> get_attester_indices(
      BeaconState state, List<PendingAttestation> attestations) throws IllegalArgumentException {

    List<ArrayList<Integer>> validator_index_sets = new ArrayList<ArrayList<Integer>>();

    for (PendingAttestation attestation : attestations) {
      validator_index_sets.add(
          get_attestation_participants(
              state, attestation.getData(), attestation.getAggregation_bitfield().toArray()));
    }

    List<Integer> attester_indices = new ArrayList<Integer>();
    for (List<Integer> validator_index_set : validator_index_sets) {
      for (Integer validator_index : validator_index_set) {
        if (!attester_indices.contains(validator_index)) {
          attester_indices.add(validator_index);
        }
      }
    }
    return attester_indices;
  }

  /**
   * Returns the total attesting for the attester indices
   *
   * @param state
   * @param attester_indices
   * @return UnsignedLong TOTAL_ATTESTING_BALANCE
   */
  public static UnsignedLong get_total_attesting_balance(
      BeaconState state, List<Integer> attester_indices) {
    UnsignedLong attesting_balance = UnsignedLong.ZERO;
    for (Integer attester_index : attester_indices) {
      attesting_balance =
          attesting_balance.plus(BeaconStateUtil.get_effective_balance(state, attester_index));
    }

    return attesting_balance;
  }

  public static int ceil_div8(int input) {
    return (int) Math.ceil(((double) input) / 8.0d);
  }

  /**
   * get indices of validators attesting to state for the given block_root TODO: the union part
   * takes O(n^2) time, where n is the number of validators. OPTIMIZE
   *
   * @param state
   * @param crosslink_committee
   * @param shard_block_root
   * @return List<Integer>
   * @throws IllegalArgumentException
   */
  public static List<Integer> attesting_validator_indices(
      BeaconState state, CrosslinkCommittee crosslink_committee, Bytes32 shard_block_root)
      throws IllegalArgumentException {
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);
    List<PendingAttestation> combined_attestations = get_epoch_attestations(state, current_epoch);
    combined_attestations.addAll(get_epoch_attestations(state, previous_epoch));
    List<ArrayList<Integer>> validator_index_sets = new ArrayList<>();
    for (PendingAttestation attestation : combined_attestations) {
      if (attestation.getData().getShard().compareTo(crosslink_committee.getShard()) == 0
          && attestation.getData().getCrosslink_data_root().equals(shard_block_root)) {
        validator_index_sets.add(
            get_attestation_participants(
                state, attestation.getData(), attestation.getAggregation_bitfield().toArray()));
      }
    }

    // TODO: .contains() method call is an O(n) operation. OPTIMIZE
    List<Integer> attesting_validator_indices = new ArrayList<Integer>();
    for (List<Integer> validator_index_set : validator_index_sets) {
      for (Integer validator_index : validator_index_set) {
        if (!attesting_validator_indices.contains(validator_index)) {
          attesting_validator_indices.add(validator_index);
        }
      }
    }
    return attesting_validator_indices;
  }

  /**
   * is the shard_block_root that was voted on by the most validators (by balance).
   *
   * @param state
   * @param crosslink_committee
   * @return Bytes32
   * @throws IllegalArgumentException
   */
  public static Bytes32 winning_root(BeaconState state, CrosslinkCommittee crosslink_committee)
      throws IllegalArgumentException {
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);
    List<PendingAttestation> combined_attestations = get_epoch_attestations(state, current_epoch);
    combined_attestations.addAll(get_epoch_attestations(state, previous_epoch));

    Map<Bytes32, UnsignedLong> shard_balances = new HashMap<>();
    for (PendingAttestation attestation : combined_attestations) {
      if (attestation.getData().getShard().compareTo(crosslink_committee.getShard()) == 0) {
        List<Integer> attesting_indices =
            get_attestation_participants(
                state, attestation.getData(), attestation.getAggregation_bitfield().toArray());
        UnsignedLong attesting_balance =
            BeaconStateUtil.get_total_balance(state, attesting_indices);
        if (shard_balances.containsKey(attestation.getData().getCrosslink_data_root())) {
          shard_balances.put(
              attestation.getData().getCrosslink_data_root(),
              shard_balances
                  .get(attestation.getData().getCrosslink_data_root())
                  .plus(attesting_balance));
        } else {
          shard_balances.put(attestation.getData().getCrosslink_data_root(), attesting_balance);
        }
      }
    }

    UnsignedLong winning_root_balance = UnsignedLong.ZERO;
    // The spec currently has no way of handling uninitialized winning_root
    Bytes32 winning_root = Bytes32.ZERO;
    for (Bytes32 shard_block_root : shard_balances.keySet()) {
      if (shard_balances.get(shard_block_root).compareTo(winning_root_balance) > 0) {
        winning_root_balance = shard_balances.get(shard_block_root);
        winning_root = shard_block_root;
      } else if (shard_balances.get(shard_block_root).compareTo(winning_root_balance) == 0) {
        if (shard_block_root
                .toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN)
                .compareTo(winning_root.toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN))
            > 0) {
          winning_root = shard_block_root;
        }
      }
    }
    return winning_root;
  }

  /**
   * get indices of validators attesting to state for the winning block root
   *
   * @param state
   * @param crosslink_committee
   * @return List<Integer>
   * @throws IllegalArgumentException
   */
  public static List<Integer> attesting_validators(
      BeaconState state, CrosslinkCommittee crosslink_committee) throws IllegalArgumentException {
    return attesting_validator_indices(
        state, crosslink_committee, winning_root(state, crosslink_committee));
  }

  /**
   * get total balance of validators attesting to state for the given block_root
   *
   * @param state
   * @param crosslink_committee
   * @return UnsignedLong
   */
  public static UnsignedLong total_attesting_balance(
      BeaconState state, CrosslinkCommittee crosslink_committee) {
    List<Integer> attesting_validators = attesting_validators(state, crosslink_committee);
    LOG.log(Level.DEBUG, "Attesting validators: " + attesting_validators);
    return BeaconStateUtil.get_total_balance(state, attesting_validators);
  }

  /**
   * Returns a pendingAttestion
   *
   * @param state
   * @param index
   * @return PendingAttestation
   * @throws IllegalArgumentException
   */
  public static PendingAttestation inclusion_slot_attestation(BeaconState state, Integer index)
      throws IllegalArgumentException {
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);

    List<PendingAttestation> previous_epoch_attestations =
        get_epoch_attestations(state, previous_epoch);

    List<PendingAttestation> possible_attestations = new ArrayList<>();
    for (PendingAttestation attestation : previous_epoch_attestations) {
      List<Integer> attestation_participants =
          get_attestation_participants(
              state, attestation.getData(), attestation.getAggregation_bitfield().toArray());
      if (attestation_participants.contains(index)) {
        possible_attestations.add(attestation);
      }
    }

    PendingAttestation lowest_inclusion_slot_attestation =
        Collections.min(possible_attestations, Comparator.comparing(a -> a.getInclusionSlot()));

    return lowest_inclusion_slot_attestation;
  }

  /**
   * Returns the inclusion slot.
   *
   * @param state
   * @param index
   * @return UnsignedLong
   * @throws IllegalArgumentException
   */
  public static UnsignedLong inclusion_slot(BeaconState state, Integer index)
      throws IllegalArgumentException {
    PendingAttestation lowest_inclusion_slot_attestation = inclusion_slot_attestation(state, index);
    return lowest_inclusion_slot_attestation.getInclusionSlot();
  }

  /**
   * Returns the inclusion distance.
   *
   * @param state
   * @param index
   * @return UnsignedLong
   */
  public static UnsignedLong inclusion_distance(BeaconState state, Integer index) {
    PendingAttestation lowest_inclusion_slot_attestation = inclusion_slot_attestation(state, index);
    return lowest_inclusion_slot_attestation
        .getInclusionSlot()
        .minus(lowest_inclusion_slot_attestation.getData().getSlot());
  }

  /**
   * Returns true if the attestation is verified
   *
   * @param state
   * @param attestation
   * @return boolean
   */
  public static boolean verifyAttestation(BeaconState state, Attestation attestation) {
    return true;
  }

  /**
   * Creates attestations for all the Validators in our validator set, given that they are in
   * CrosslinkCommittees that are appointed to attest
   *
   * @param headState
   * @param headBlock
   * @param validatorSet
   * @return attestations
   */
  public static List<Attestation> createAttestations(
      BeaconState headState,
      BeaconBlock headBlock,
      HashMap<BLSPublicKey, BLSKeyPair> validatorSet) {

    // Get variables necessary that can be shared among Attestations of all validators
    UnsignedLong slot = headState.getSlot();
    ArrayList<CrosslinkCommittee> crosslinkCommittees =
        BeaconStateUtil.get_crosslink_committees_at_slot(headState, slot);
    Bytes32 headBlockRoot = HashTreeUtil.hash_tree_root(headBlock.toBytes());
    Bytes32 crosslinkDataRoot = Bytes32.ZERO;
    UnsignedLong epochStartSlot =
        BeaconStateUtil.get_epoch_start_slot(BeaconStateUtil.slot_to_epoch(slot));
    Bytes32 epochBoundaryRoot;
    if (epochStartSlot.compareTo(slot) == 0) {
      epochBoundaryRoot = HashTreeUtil.hash_tree_root(headBlock.toBytes());
    } else {
      epochBoundaryRoot = BeaconStateUtil.get_block_root(headState, epochStartSlot);
    }
    UnsignedLong justifiedEpoch = headState.getJustified_epoch();
    Bytes32 justifiedBlockRoot =
        BeaconStateUtil.get_block_root(
            headState, BeaconStateUtil.get_epoch_start_slot(justifiedEpoch));

    // Create attestations specific to each Validator
    List<Attestation> attestations = new ArrayList<>();
    for (CrosslinkCommittee crosslinkCommittee : crosslinkCommittees) {
      for (Integer validatorIndex : crosslinkCommittee.getCommittee()) {

        // Skip if attester is in not in our validatorSet
        BLSPublicKey attesterPubkey =
            headState.getValidator_registry().get(validatorIndex).getPubkey();
        if (!validatorSet.containsKey(attesterPubkey)) {
          continue;
        }

        // Get variables specific to each Attestation
        UnsignedLong shard = crosslinkCommittee.getShard();
        Crosslink latestCrosslink =
            headState.getLatest_crosslinks().get(shard.intValue() % Constants.SHARD_COUNT);

        // Set attestation data
        AttestationData attestationData =
            new AttestationData(
                slot,
                shard,
                headBlockRoot,
                epochBoundaryRoot,
                crosslinkDataRoot,
                latestCrosslink,
                justifiedEpoch,
                justifiedBlockRoot);

        // Create aggregation bitfield
        int indexIntoCommittee = crosslinkCommittee.getCommittee().indexOf(validatorIndex);
        int array_length = Math.toIntExact((crosslinkCommittee.getCommittee().size() + 7) / 8);
        byte[] aggregation_bitfield = new byte[array_length];
        aggregation_bitfield[indexIntoCommittee / 8] =
            (byte)
                (aggregation_bitfield[indexIntoCommittee / 8]
                    | (byte) Math.pow(2, (indexIntoCommittee % 8)));

        // Create custody_bitfield
        Bytes custody_bitfield = Bytes.wrap(new byte[array_length]);
        AttestationDataAndCustodyBit attestation_data_and_custody_bit =
            new AttestationDataAndCustodyBit(attestationData, false);

        // Sign attestation data
        Bytes32 attestation_message_to_sign =
            HashTreeUtil.hash_tree_root(attestation_data_and_custody_bit.toBytes());
        BLSSignature signed_attestation_data =
            BLSSignature.sign(
                validatorSet.get(attesterPubkey),
                attestation_message_to_sign,
                BeaconStateUtil.get_domain(
                        headState.getFork(),
                        BeaconStateUtil.slot_to_epoch(attestationData.getSlot()),
                        Constants.DOMAIN_ATTESTATION)
                    .longValue());

        // Form attestation
        Attestation attestation =
            new Attestation(
                Bytes.wrap(aggregation_bitfield),
                attestationData,
                custody_bitfield,
                signed_attestation_data);

        attestations.add(attestation);
      }
    }
    return attestations;
  }

  public static List<Attestation> getAttestationsUntilSlot(
      PriorityBlockingQueue<Attestation> attestationsQueue, UnsignedLong slot) {
    List<Attestation> attestations = new ArrayList<>();
    if (Objects.nonNull(attestationsQueue) && attestationsQueue.size() > 0) {
      while (Objects.nonNull(attestationsQueue.peek())
          && attestationsQueue.peek().getSlot().compareTo(slot) <= 0) {
        attestations.add(attestationsQueue.remove());
      }
    }
    return attestations;
  }
}

package tech.pegasys.artemis.validator.coordinator;

import com.google.common.primitives.UnsignedLong;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Committee;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_block_root_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;

public class ValidatorCoordinatorUtil {

  public static List<Triple<BLSPublicKey, Integer, Committee>> getAttesterInformation(
          BeaconState headState,
          HashMap<UnsignedLong, List<AttestationAssignment>> attestationAssignments) {
    UnsignedLong slot = headState.getSlot();
    return getAttesterInformation(headState, attestationAssignments, slot);
  }

  public static List<Triple<BLSPublicKey, Integer, Committee>> getAttesterInformation(
          BeaconState state,
          HashMap<UnsignedLong, List<AttestationAssignment>> attestationAssignments,
          final UnsignedLong slot) {
    List<AttestationAssignment> attestationAssignmentsForSlot =
            attestationAssignments.get(slot);
    List<Triple<BLSPublicKey, Integer, Committee>> attesters = new ArrayList<>();
    if (attestationAssignmentsForSlot != null) {
      for (int i = 0; i < attestationAssignmentsForSlot.size(); i++) {
        AttestationAssignment attestationAssignment = attestationAssignmentsForSlot.get(i);
        int validatorIndex = attestationAssignment.getValidatorIndex();
        List<Integer> committee = attestationAssignment.getCommittee();
        UnsignedLong committeeIndex = attestationAssignment.getCommitteIndex();
        int indexIntoCommittee = committee.indexOf(validatorIndex);
        if (attestationAssignment.isAggregator()) {
          System.out.println("Validator " + validatorIndex + " is an aggregator in committee " + committeeIndex);
        }

        Committee crosslinkCommittee = new Committee(committeeIndex, committee);
        attesters.add(
                new MutableTriple<>(
                        state.getValidators().get(validatorIndex).getPubkey(),
                        indexIntoCommittee,
                        crosslinkCommittee));
      }
    }
    return attesters;
  }

  // Get attestation data that does not include attester specific shard or crosslink information
  public static AttestationData getGenericAttestationData(BeaconState state, BeaconBlock block) {
    UnsignedLong slot = state.getSlot();
    // Get variables necessary that can be shared among Attestations of all validators
    Bytes32 beacon_block_root = block.signing_root("signature");
    UnsignedLong start_slot = compute_start_slot_at_epoch(get_current_epoch(state));
    Bytes32 epoch_boundary_block_root =
            start_slot.compareTo(slot) == 0
                    ? block.signing_root("signature")
                    : get_block_root_at_slot(state, start_slot);
    Checkpoint source = state.getCurrent_justified_checkpoint();
    Checkpoint target = new Checkpoint(get_current_epoch(state), epoch_boundary_block_root);

    // Set attestation data
    return new AttestationData(slot, UnsignedLong.ZERO, beacon_block_root, source, target);
  }
}

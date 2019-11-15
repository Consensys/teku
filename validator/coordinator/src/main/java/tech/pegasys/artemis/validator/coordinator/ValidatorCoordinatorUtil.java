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

package tech.pegasys.artemis.validator.coordinator;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_block_root_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

public class ValidatorCoordinatorUtil {

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

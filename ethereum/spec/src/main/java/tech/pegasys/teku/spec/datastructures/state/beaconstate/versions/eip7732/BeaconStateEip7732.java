/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732;

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.LATEST_BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.LATEST_FULL_SLOT;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.LATEST_WITHDRAWALS_ROOT;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;

public interface BeaconStateEip7732 extends BeaconStateElectra {
  static BeaconStateEip7732 required(final BeaconState state) {
    return state
        .toVersionEip7732()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an Eip7732 state but got: " + state.getClass().getSimpleName()));
  }

  static void describeCustomEip7732Fields(
      final MoreObjects.ToStringHelper stringBuilder, final BeaconStateEip7732 state) {
    BeaconStateElectra.describeCustomElectraFields(stringBuilder, state);
    stringBuilder.add("latest_block_hash", state.getLatestBlockHash());
    stringBuilder.add("latest_full_slot", state.getLatestFullSlot());
    stringBuilder.add("latest_withdrawals_root", state.getLatestWithdrawalsRoot());
  }

  @Override
  MutableBeaconStateEip7732 createWritableCopy();

  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateEip7732 updatedEip7732(
          final Mutator<MutableBeaconStateEip7732, E1, E2, E3> mutator) throws E1, E2, E3 {
    MutableBeaconStateEip7732 writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  default Optional<BeaconStateEip7732> toVersionEip7732() {
    return Optional.of(this);
  }

  default Bytes32 getLatestBlockHash() {
    final int index = getSchema().getFieldIndex(LATEST_BLOCK_HASH);
    return ((SszBytes32) get(index)).get();
  }

  default UInt64 getLatestFullSlot() {
    final int index = getSchema().getFieldIndex(LATEST_FULL_SLOT);
    return ((SszUInt64) get(index)).get();
  }

  default Bytes32 getLatestWithdrawalsRoot() {
    final int index = getSchema().getFieldIndex(LATEST_WITHDRAWALS_ROOT);
    return ((SszBytes32) get(index)).get();
  }
}

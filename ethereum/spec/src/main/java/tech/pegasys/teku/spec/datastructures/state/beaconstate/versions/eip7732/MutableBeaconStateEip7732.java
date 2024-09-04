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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;

public interface MutableBeaconStateEip7732 extends MutableBeaconStateElectra, BeaconStateEip7732 {
  static MutableBeaconStateEip7732 required(final MutableBeaconState state) {
    return state
        .toMutableVersionEip7732()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an Eip7732 state but got: " + state.getClass().getSimpleName()));
  }

  @Override
  BeaconStateEip7732 commitChanges();

  @Override
  default Optional<MutableBeaconStateEip7732> toMutableVersionEip7732() {
    return Optional.of(this);
  }

  default void setLatestBlockHash(final Bytes32 latestBlockHash) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.LATEST_BLOCK_HASH);
    set(fieldIndex, SszBytes32.of(latestBlockHash));
  }

  default void setLatestFullSlot(final UInt64 latestFullSlot) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.LATEST_FULL_SLOT);
    set(fieldIndex, SszUInt64.of(latestFullSlot));
  }

  default void setLatestWithdrawalsRoot(final Bytes32 latestWithdrawalsRoot) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.LATEST_WITHDRAWALS_ROOT);
    set(fieldIndex, SszBytes32.of(latestWithdrawalsRoot));
  }
}

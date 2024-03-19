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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.MutableBeaconStateDeneb;

public interface MutableBeaconStateElectra extends MutableBeaconStateDeneb, BeaconStateElectra {

  @Override
  BeaconStateElectra commitChanges();

  default void setPreviousProposerIndex(UInt64 previousProposerIndex) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_PROPOSER_INDEX);
    set(fieldIndex, SszUInt64.of(previousProposerIndex));
  }

  @Override
  default Optional<MutableBeaconStateElectra> toMutableVersionElectra() {
    return Optional.of(this);
  }
}

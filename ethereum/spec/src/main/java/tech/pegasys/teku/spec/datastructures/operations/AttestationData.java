/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.operations;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public interface AttestationData extends SszContainer {

  default UInt64 getEarliestSlotForForkChoice(final Spec spec) {
    final UInt64 targetEpochStartSlot = getTarget().getEpochStartSlot(spec);
    return getEarliestSlotForForkChoice(targetEpochStartSlot);
  }

  default UInt64 getEarliestSlotForForkChoice(final MiscHelpers miscHelpers) {
    final UInt64 targetEpochStartSlot = miscHelpers.computeStartSlotAtEpoch(getTarget().getEpoch());
    return getEarliestSlotForForkChoice(targetEpochStartSlot);
  }

  default UInt64 getEarliestSlotForForkChoice(final UInt64 targetEpochStartSlot) {
    // Attestations can't be processed by fork choice until their slot is in the past and until we
    // are in the same epoch as their target.
    return getSlot().plus(UInt64.ONE).max(targetEpochStartSlot);
  }

  UInt64 getSlot();

  Optional<UInt64> getIndex();

  default UInt64 getIndexRequired() {
    return getIndex()
        .orElseThrow(() -> new IllegalArgumentException("Missing attestation data index"));
  }

  Optional<UInt64> getPayloadStatus();

  default UInt64 getPayloadStatusRequired() {
    return getPayloadStatus()
        .orElseThrow(() -> new IllegalArgumentException("Missing attestation data payload status"));
  }

  Bytes32 getBeaconBlockRoot();

  Checkpoint getSource();

  Checkpoint getTarget();

  @Override
  AttestationDataSchema<? extends AttestationData> getSchema();
}

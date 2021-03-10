/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate;

import static com.google.common.base.Preconditions.checkState;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives;
import tech.pegasys.teku.ssz.sos.SszField;

class BeaconStateInvariants {
  // Schemas
  static final SszSchema<SszPrimitives.SszUInt64> GENESIS_TIME_SCHEMA =
      SszPrimitiveSchemas.UINT64_SCHEMA;
  static final SszSchema<SszPrimitives.SszBytes32> GENESIS_VALIDATORS_ROOT_SCHEMA =
      SszPrimitiveSchemas.BYTES32_SCHEMA;
  static final SszSchema<SszPrimitives.SszUInt64> SLOT_SCHEMA = SszPrimitiveSchemas.UINT64_SCHEMA;

  // Fields
  static SszField GENESIS_TIME_FIELD =
      new SszField(0, BeaconStateFields.GENESIS_TIME.name(), GENESIS_TIME_SCHEMA);
  static SszField GENESIS_VALIDATORS_ROOT_FIELD =
      new SszField(
          1, BeaconStateFields.GENESIS_VALIDATORS_ROOT.name(), GENESIS_VALIDATORS_ROOT_SCHEMA);
  static SszField SLOT_FIELD = new SszField(2, BeaconStateFields.SLOT.name(), SLOT_SCHEMA);

  // Return list of invariant fields
  public static List<SszField> getInvariantFields() {
    return List.of(GENESIS_TIME_FIELD, GENESIS_VALIDATORS_ROOT_FIELD, SLOT_FIELD);
  }

  public static UInt64 extractSlot(final Bytes bytes) {
    // Check assumptions
    checkState(GENESIS_TIME_SCHEMA.isFixedSize(), "Expected genesisTime field to be a fixed size");
    checkState(
        GENESIS_VALIDATORS_ROOT_SCHEMA.isFixedSize(),
        "Expected genesisValidatorsRoot field to be a fixed size");
    checkState(SLOT_SCHEMA.isFixedSize(), "Expected slot field to be a fixed size");

    final int offset =
        GENESIS_TIME_SCHEMA.getSszFixedPartSize()
            + GENESIS_VALIDATORS_ROOT_SCHEMA.getSszFixedPartSize();
    final int size = SLOT_SCHEMA.getSszFixedPartSize();

    // Extract slot data
    final Bytes slotData = bytes.slice(offset, size);
    return SLOT_SCHEMA.sszDeserialize(slotData).get();
  }
}

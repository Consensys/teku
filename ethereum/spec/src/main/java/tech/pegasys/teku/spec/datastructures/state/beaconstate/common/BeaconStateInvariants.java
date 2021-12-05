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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class BeaconStateInvariants {
  // Schemas
  static final SszSchema<SszUInt64> GENESIS_TIME_SCHEMA = SszPrimitiveSchemas.UINT64_SCHEMA;
  static final SszSchema<SszBytes32> GENESIS_VALIDATORS_ROOT_SCHEMA =
      SszPrimitiveSchemas.BYTES32_SCHEMA;
  static final SszSchema<SszUInt64> SLOT_SCHEMA = SszPrimitiveSchemas.UINT64_SCHEMA;

  // Fields
  static SszField GENESIS_TIME_FIELD =
      new SszField(0, BeaconStateFields.GENESIS_TIME.name(), GENESIS_TIME_SCHEMA);
  static SszField GENESIS_VALIDATORS_ROOT_FIELD =
      new SszField(
          1, BeaconStateFields.GENESIS_VALIDATORS_ROOT.name(), GENESIS_VALIDATORS_ROOT_SCHEMA);
  static SszField SLOT_FIELD = new SszField(2, BeaconStateFields.SLOT.name(), SLOT_SCHEMA);

  // Return list of invariant fields
  static List<SszField> getInvariantFields() {
    return List.of(GENESIS_TIME_FIELD, GENESIS_VALIDATORS_ROOT_FIELD, SLOT_FIELD);
  }

  /**
   * Extract the slot value from any serialized state
   *
   * @param bytes A serialized state
   * @return The slot of the state
   */
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

  static boolean equals(BeaconState state, Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (state == obj) {
      return true;
    }

    if (!(obj instanceof BeaconState)) {
      return false;
    }

    BeaconState other = (BeaconState) obj;
    return state.hashTreeRoot().equals(other.hashTreeRoot());
  }

  static int hashCode(BeaconState state) {
    return state.hashTreeRoot().slice(0, 4).toInt();
  }

  static String toString(BeaconState state, final Consumer<ToStringHelper> modifier) {
    final ToStringHelper builder =
        MoreObjects.toStringHelper(state)
            .add("genesis_time", state.getGenesis_time())
            .add("genesis_validators_root", state.getGenesis_validators_root())
            .add("slot", state.getSlot())
            .add("fork", state.getFork())
            .add("latest_block_header", state.getLatest_block_header())
            .add("block_roots", state.getBlock_roots())
            .add("state_roots", state.getState_roots())
            .add("historical_roots", state.getHistorical_roots())
            .add("eth1_data", state.getEth1_data())
            .add("eth1_data_votes", state.getEth1_data_votes())
            .add("eth1_deposit_index", state.getEth1_deposit_index())
            .add("validators", state.getValidators())
            .add("balances", state.getBalances())
            .add("randao_mixes", state.getRandao_mixes())
            .add("slashings", state.getSlashings())
            .add("justification_bits", state.getJustification_bits())
            .add("previous_justified_checkpoint", state.getPrevious_justified_checkpoint())
            .add("current_justified_checkpoint", state.getCurrent_justified_checkpoint())
            .add("finalized_checkpoint", state.getFinalized_checkpoint());

    modifier.accept(builder);
    return builder.toString();
  }
}

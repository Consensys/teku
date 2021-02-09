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

package tech.pegasys.teku.spec.datastructures.state;

import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.genesis.BeaconStateGenesis;
import tech.pegasys.teku.spec.datastructures.state.hf1.BeaconStateHF1;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.SszContainer;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives;

public interface BeaconState extends SszContainer {

  // Versioning
  default UInt64 getGenesis_time() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.GENESIS_TIME.name());
    return ((SszPrimitives.SszUInt64) get(fieldIndex)).get();
  }

  default Bytes32 getGenesis_validators_root() {
    // TODO
    return null;
  }

  default UInt64 getSlot() {
    // TODO
    return null;
  }

  default Fork getFork() {
    // TODO
    return null;
  }

  default ForkInfo getForkInfo() {
    // TODO
    return null;
  }

  // History
  default BeaconBlockHeader getLatest_block_header() {
    // TODO
    return null;
  }

  default SSZVector<Bytes32> getBlock_roots() {
    // TODO
    return null;
  }

  default SSZVector<Bytes32> getState_roots() {
    // TODO
    return null;
  }

  default SSZList<Bytes32> getHistorical_roots() {
    // TODO
    return null;
  }

  // Eth1
  default Eth1Data getEth1_data() {
    // TODO
    return null;
  }

  default SSZList<Eth1Data> getEth1_data_votes() {
    // TODO
    return null;
  }

  default UInt64 getEth1_deposit_index() {
    // TODO
    return null;
  }

  // Registry
  default SSZList<Validator> getValidators() {
    // TODO
    return null;
  }

  default SSZList<UInt64> getBalances() {
    // TODO
    return null;
  }

  default SSZVector<Bytes32> getRandao_mixes() {
    // TODO
    return null;
  }

  // Slashings
  default SSZVector<UInt64> getSlashings() {
    // TODO
    return null;
  }

  // Finality
  default Bitvector getJustification_bits() {
    // TODO
    return null;
  }

  default Checkpoint getPrevious_justified_checkpoint() {
    // TODO
    return null;
  }

  default Checkpoint getCurrent_justified_checkpoint() {
    // TODO
    return null;
  }

  default Checkpoint getFinalized_checkpoint() {
    // TODO
    return null;
  }

  // Update
  BeaconState updated(Consumer<MutableBeaconState> updater);

  // Version-specific casting
  default Optional<BeaconStateGenesis> toGenesisVersion() {
    return Optional.empty();
  }

  default Optional<BeaconStateHF1> toHF1Version() {
    return Optional.empty();
  }
}

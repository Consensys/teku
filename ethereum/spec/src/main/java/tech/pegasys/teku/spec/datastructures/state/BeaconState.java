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

public interface BeaconState extends SszContainer {

  // Versioning
  UInt64 getGenesis_time();

  Bytes32 getGenesis_validators_root();

  UInt64 getSlot();

  Fork getFork();

  ForkInfo getForkInfo();

  // History
  BeaconBlockHeader getLatest_block_header();

  SSZVector<Bytes32> getBlock_roots();

  SSZVector<Bytes32> getState_roots();

  SSZList<Bytes32> getHistorical_roots();

  // Eth1
  Eth1Data getEth1_data();

  SSZList<Eth1Data> getEth1_data_votes();

  UInt64 getEth1_deposit_index();

  // Registry
  SSZList<Validator> getValidators();

  SSZList<UInt64> getBalances();

  SSZVector<Bytes32> getRandao_mixes();

  // Slashings
  SSZVector<UInt64> getSlashings();

  // Finality
  Bitvector getJustification_bits();

  Checkpoint getPrevious_justified_checkpoint();

  Checkpoint getCurrent_justified_checkpoint();

  Checkpoint getFinalized_checkpoint();

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

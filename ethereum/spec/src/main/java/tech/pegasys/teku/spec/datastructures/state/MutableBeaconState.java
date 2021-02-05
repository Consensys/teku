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
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives;

public interface MutableBeaconState extends BeaconState {

  // Versioning
  void setGenesis_time(UInt64 genesis_time);

  void setGenesis_validators_root(Bytes32 genesis_validators_root);

  void setSlot(UInt64 slot);

  void setFork(Fork fork);

  // History
  void setLatest_block_header(BeaconBlockHeader latest_block_header);

  void updateBlock_roots(Consumer<SSZMutableVector<Bytes32>> updater);

  void updateState_roots(Consumer<SSZMutableVector<Bytes32>> updater);

  void updateHistorical_roots(Consumer<SSZMutableList<Bytes32>> updater);

  // Eth1
  void setEth1_data(Eth1Data eth1_data);

  void updateEth1_data_votes(Consumer<SSZMutableList<Eth1Data>> updater);

  void setEth1_deposit_index(UInt64 eth1_deposit_index);

  // Registry
  void updateValidators(Consumer<SSZMutableList<Validator>> updater);

  void updateBalances(Consumer<SSZMutableList<UInt64>> updater);

  void updateRandao_mixes(Consumer<SSZMutableVector<Bytes32>> updater);

  // Slashings
  void updateSlashings(Consumer<SSZMutableVector<UInt64>> updater);

  // Finality
  void setJustification_bits(Bitvector justification_bits);

  void setPrevious_justified_checkpoint(Checkpoint previous_justified_checkpoint);

  void setCurrent_justified_checkpoint(Checkpoint current_justified_checkpoint);

  void setFinalized_checkpoint(Checkpoint finalized_checkpoint);

  /* Variable Fields */

  // Attestations
  void maybeUpdatePrevious_epoch_attestations(
      Consumer<Optional<SSZMutableList<PendingAttestation>>> updater);

  void maybeUpdateCurrent_epoch_attestations(
      Consumer<Optional<SSZMutableList<PendingAttestation>>> updater);

  // Participation
  void maybeUpdatePreviousEpochParticipation(
      Consumer<Optional<SSZMutableList<SSZVector<SszPrimitives.SszBit>>>> updater);

  void maybeUpdateCurrentEpochParticipation(
      Consumer<Optional<SSZMutableList<SSZVector<SszPrimitives.SszBit>>>> updater);
}

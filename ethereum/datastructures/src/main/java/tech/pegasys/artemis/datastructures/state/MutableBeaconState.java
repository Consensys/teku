/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableRefList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableVector;
import tech.pegasys.artemis.util.backing.ContainerViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;

public interface MutableBeaconState
    extends BeaconState, ContainerViewWriteRef<ViewRead, ViewWrite> {

  // Versioning

  void setGenesis_time(UnsignedLong genesis_time);

  void setSlot(UnsignedLong slot);

  void setFork(Fork fork);

  // History
  void setLatest_block_header(BeaconBlockHeader latest_block_header);

  @Override
  SSZMutableVector<Bytes32> getBlock_roots();

  @Override
  SSZMutableVector<Bytes32> getState_roots();

  @Override
  SSZMutableList<Bytes32> getHistorical_roots();

  // Eth1
  void setEth1_data(Eth1Data eth1_data);

  @Override
  SSZMutableList<Eth1Data> getEth1_data_votes();

  void setEth1_deposit_index(UnsignedLong eth1_deposit_index);

  // Registry
  @Override
  SSZMutableRefList<Validator, MutableValidator> getValidators();

  @Override
  SSZMutableList<UnsignedLong> getBalances();

  @Override
  SSZMutableVector<Bytes32> getRandao_mixes();

  // Slashings
  @Override
  SSZMutableVector<UnsignedLong> getSlashings();

  // Attestations
  @Override
  SSZMutableList<PendingAttestation> getPrevious_epoch_attestations();

  @Override
  SSZMutableList<PendingAttestation> getCurrent_epoch_attestations();

  // Finality
  void setJustification_bits(Bitvector justification_bits);

  void setPrevious_justified_checkpoint(Checkpoint previous_justified_checkpoint);

  void setCurrent_justified_checkpoint(Checkpoint current_justified_checkpoint);

  void setFinalized_checkpoint(Checkpoint finalized_checkpoint);

  @Override
  BeaconState commitChanges();
}

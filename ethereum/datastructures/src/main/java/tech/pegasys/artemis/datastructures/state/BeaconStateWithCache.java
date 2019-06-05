/*
 * Copyright 2019 ConsenSys AG.
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;

public final class BeaconStateWithCache extends BeaconState {

  protected int currentBeaconProposerIndex = -1;
  protected List<CrosslinkCommittee> crosslinkCommitteesAtSlot = null;

  public BeaconStateWithCache() {
    super();
    this.currentBeaconProposerIndex = -1;
    this.crosslinkCommitteesAtSlot = null;
  }

  public BeaconStateWithCache(BeaconStateWithCache state) {
    this.slot = state.getSlot();
    this.genesis_time = state.getGenesis_time();
    this.fork = new Fork(state.getFork());

    this.validator_registry = this.copyList(state.getValidator_registry(), new ArrayList<>());
    this.validator_balances = state.getValidator_balances().stream().collect(Collectors.toList());
    this.validator_registry_update_epoch = state.getValidator_registry_update_epoch();

    this.latest_randao_mixes =
        this.copyBytesList(state.getLatest_randao_mixes(), new ArrayList<>());
    this.previous_shuffling_start_shard = state.getPrevious_shuffling_start_shard();
    this.current_shuffling_start_shard = state.getCurrent_shuffling_start_shard();
    this.previous_shuffling_epoch = state.getPrevious_shuffling_epoch();
    this.current_shuffling_epoch = state.getCurrent_shuffling_epoch();
    this.previous_shuffling_seed = state.getPrevious_shuffling_seed();
    this.current_shuffling_seed = state.getCurrent_shuffling_seed();

    this.previous_epoch_attestations =
        this.copyList(state.getPrevious_epoch_attestations(), new ArrayList<>());
    this.current_epoch_attestations =
        this.copyList(state.getCurrent_epoch_attestations(), new ArrayList<>());
    this.previous_justified_epoch = state.getPrevious_justified_epoch();
    this.current_justified_epoch = state.getCurrent_justified_epoch();
    this.previous_justified_root = state.getPrevious_justified_root();
    this.current_justified_root = state.getCurrent_justified_root();
    this.justification_bitfield = state.getJustification_bitfield();
    this.finalized_epoch = state.getFinalized_epoch();
    this.finalized_root = state.getFinalized_root();

    this.latest_crosslinks = this.copyList(state.getLatest_crosslinks(), new ArrayList<>());
    this.latest_block_roots = this.copyBytesList(state.getLatest_block_roots(), new ArrayList<>());
    this.latest_state_roots = this.copyBytesList(state.getLatest_state_roots(), new ArrayList<>());
    this.latest_active_index_roots =
        this.copyBytesList(state.getLatest_active_index_roots(), new ArrayList<>());
    this.latest_slashed_balances =
        state.getLatest_slashed_balances().stream().collect(Collectors.toList());
    this.latest_block_header =
        BeaconBlockHeader.fromBytes(state.getLatest_block_header().toBytes());
    this.historical_roots = this.copyBytesList(state.getHistorical_roots(), new ArrayList<>());
    this.latest_eth1_data = new Eth1Data(state.getLatest_eth1_data());
    this.eth1_data_votes = this.copyList(state.getEth1_data_votes(), new ArrayList<>());
    this.deposit_index = state.getDeposit_index();
  }

  private <S extends Copyable<S>, T extends List<S>> T copyList(T sourceList, T destinationList) {
    for (S sourceItem : sourceList) {
      destinationList.add(sourceItem.copy());
    }
    return destinationList;
  }

  private <T extends List<Bytes32>> T copyBytesList(T sourceList, T destinationList) {
    for (Bytes sourceItem : sourceList) {
      destinationList.add((Bytes32) sourceItem.copy());
    }
    return destinationList;
  }

  public static BeaconStateWithCache deepCopy(BeaconStateWithCache state) {
    return new BeaconStateWithCache(state);
  }

  public int getCurrentBeaconProposerIndex() {
    return this.currentBeaconProposerIndex;
  }

  public void setCurrentBeaconProposerIndex(int currentBeaconProposerIndex) {
    this.currentBeaconProposerIndex = currentBeaconProposerIndex;
  }

  public List<CrosslinkCommittee> getCrossLinkCommitteesAtSlot() {
    return this.crosslinkCommitteesAtSlot;
  }

  public void setCrossLinkCommitteesAtSlot(List<CrosslinkCommittee> crossLinkCommittees) {
    this.crosslinkCommitteesAtSlot = crossLinkCommittees;
  }

  public void invalidateCache() {
    this.currentBeaconProposerIndex = -1;
    this.crosslinkCommitteesAtSlot = null;
  }
}

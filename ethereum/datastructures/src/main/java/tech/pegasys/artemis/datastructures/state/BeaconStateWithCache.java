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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;

public final class BeaconStateWithCache extends BeaconState {

  protected Map<UnsignedLong, UnsignedLong> epochStartShards = new HashMap<>();
  protected Map<String, List<Integer>> crosslinkCommittees = new HashMap<>();

  public BeaconStateWithCache() {
    super();
  }

  public BeaconStateWithCache(BeaconStateWithCache state) {
    this.slot = state.getSlot();
    this.genesis_time = state.getGenesis_time();
    this.fork = new Fork(state.getFork());

    this.validator_registry = this.copyList(state.getValidator_registry(), new ArrayList<>());
    this.balances = state.getBalances().stream().collect(Collectors.toList());

    this.latest_randao_mixes =
        this.copyBytesList(state.getLatest_randao_mixes(), new ArrayList<>());
    this.latest_start_shard = state.getLatest_start_shard();

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

    this.current_crosslinks = this.copyList(state.getCurrent_crosslinks(), new ArrayList<>());
    this.previous_crosslinks = this.copyList(state.getPrevious_crosslinks(), new ArrayList<>());
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
    this.eth1_data_votes = state.getEth1_data_votes().stream().collect(Collectors.toList());
    this.deposit_index = state.getDeposit_index();

    this.crosslinkCommittees = state.getCrossLinkCommittees();
    this.epochStartShards = state.getEpochStartShards();
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

  public Map<String, List<Integer>> getCrossLinkCommittees() {
    return this.crosslinkCommittees;
  }

  public List<Integer> getCrossLinkCommittee(UnsignedLong epoch, UnsignedLong shard) {
    String key = epoch.toString() + "_" + shard.toString();
    if (crosslinkCommittees.containsKey(key)) {
      return crosslinkCommittees.get(key);
    }
    return null;
  }

  public void setCrossLinkCommittee(
      List<Integer> crosslinkCommittees, UnsignedLong epoch, UnsignedLong shard) {
    this.crosslinkCommittees.put(epoch.toString() + "_" + shard.toString(), crosslinkCommittees);
  }

  public Map<UnsignedLong, UnsignedLong> getEpochStartShards() {
    return this.epochStartShards;
  }

  public UnsignedLong getEpochStartShard(UnsignedLong epoch) {
    if (epochStartShards.containsKey(epoch)) {
      return epochStartShards.get(epoch);
    }
    return null;
  }

  public void setEpochStartShard(UnsignedLong epoch, UnsignedLong shard) {
    this.epochStartShards.put(epoch, shard);
  }

  public void invalidateCache() {
    // TODO: clean this cache after finalization
    this.epochStartShards = new HashMap<>();
    this.crosslinkCommittees = new HashMap<>();
  }
}

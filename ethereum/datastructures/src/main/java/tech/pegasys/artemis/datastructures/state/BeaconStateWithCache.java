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

  protected Map<UnsignedLong, UnsignedLong> startShards = new HashMap<>();
  protected Map<String, List<Integer>> crosslinkCommittees = new HashMap<>();

  public BeaconStateWithCache() {
    super();
  }

  public BeaconStateWithCache(BeaconStateWithCache state) {
    // Versioning
    this.genesis_time = state.getGenesis_time();
    this.slot = state.getSlot();
    this.fork = new Fork(state.getFork());

    // History
    this.latest_block_header =
        BeaconBlockHeader.fromBytes(state.getLatest_block_header().toBytes());
    this.block_roots = this.copyBytesList(state.getBlock_roots(), new ArrayList<>());
    this.state_roots = this.copyBytesList(state.getState_roots(), new ArrayList<>());
    this.historical_roots = this.copyBytesList(state.getHistorical_roots(), new ArrayList<>());

    // Eth1
    this.eth1_data = new Eth1Data(state.getEth1_data());
    this.eth1_data_votes = state.getEth1_data_votes().stream().collect(Collectors.toList());
    this.eth1_deposit_index = state.getEth1_deposit_index();

    // Registry
    this.validators = this.copyList(state.getValidators(), new ArrayList<>());
    this.balances = state.getBalances().stream().collect(Collectors.toList());

    // Shuffling
    this.start_shard = state.getStart_shard();
    this.randao_mixes = this.copyBytesList(state.getRandao_mixes(), new ArrayList<>());
    this.active_index_roots = this.copyBytesList(state.getActive_index_roots(), new ArrayList<>());
    this.compact_committees_roots =
        this.copyBytesList(state.getCompact_committees_roots(), new ArrayList<>());

    // Slashings
    this.slashings = state.getSlashings().stream().collect(Collectors.toList());

    // Attestations
    this.previous_epoch_attestations =
        this.copyList(state.getPrevious_epoch_attestations(), new ArrayList<>());
    this.current_epoch_attestations =
        this.copyList(state.getCurrent_epoch_attestations(), new ArrayList<>());

    // Crosslinks
    this.current_crosslinks = this.copyList(state.getCurrent_crosslinks(), new ArrayList<>());
    this.previous_crosslinks = this.copyList(state.getPrevious_crosslinks(), new ArrayList<>());

    // Finality
    this.justification_bits = state.getJustification_bits().copy();
    this.previous_justified_checkpoint = new Checkpoint(state.getPrevious_justified_checkpoint());
    this.current_justified_checkpoint = new Checkpoint(state.getCurrent_justified_checkpoint());
    this.finalized_checkpoint = new Checkpoint(state.getFinalized_checkpoint());

    // Client Specific For Caching Purposes
    this.crosslinkCommittees = state.getCrossLinkCommittees();
    this.startShards = state.getStartShards();
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

  public Map<UnsignedLong, UnsignedLong> getStartShards() {
    return this.startShards;
  }

  public UnsignedLong getStartShard(UnsignedLong epoch) {
    if (startShards.containsKey(epoch)) {
      return startShards.get(epoch);
    }
    return null;
  }

  public void setStartShard(UnsignedLong epoch, UnsignedLong shard) {
    this.startShards.put(epoch, shard);
  }

  public void invalidateCache() {
    // TODO: clean this cache after finalization
    this.startShards = new HashMap<>();
    this.crosslinkCommittees = new HashMap<>();
  }
}

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

public final class BeaconStateWithCache extends BeaconState {

  protected Map<UnsignedLong, UnsignedLong> startShards = new HashMap<>();
  protected Map<String, List<Integer>> crosslinkCommittees = new HashMap<>();

  public BeaconStateWithCache() {
    super();
  }

  public BeaconStateWithCache(
      // Versioning
      UnsignedLong genesis_time,
      UnsignedLong slot,
      Fork fork,

      // History
      BeaconBlockHeader latest_block_header,
      SSZVector<Bytes32> block_roots,
      SSZVector<Bytes32> state_roots,
      SSZList<Bytes32> historical_roots,

      // Eth1
      Eth1Data eth1_data,
      SSZList<Eth1Data> eth1_data_votes,
      UnsignedLong eth1_deposit_index,

      // Registry
      SSZList<Validator> validators,
      SSZList<UnsignedLong> balances,

      // Shuffling
      UnsignedLong start_shard,
      SSZVector<Bytes32> randao_mixes,
      SSZVector<Bytes32> active_index_roots,
      SSZVector<Bytes32> compact_committees_roots,

      // Slashings
      SSZVector<UnsignedLong> slashings,

      // Attestations
      SSZList<PendingAttestation> previous_epoch_attestations,
      SSZList<PendingAttestation> current_epoch_attestations,

      // Crosslinks
      SSZVector<Crosslink> previous_crosslinks,
      SSZVector<Crosslink> current_crosslinks,

      // Finality
      Bitvector justification_bits,
      Checkpoint previous_justified_checkpoint,
      Checkpoint current_justified_checkpoint,
      Checkpoint finalized_checkpoint) {
    super(
        genesis_time,
        slot,
        fork,
        latest_block_header,
        block_roots,
        state_roots,
        historical_roots,
        eth1_data,
        eth1_data_votes,
        eth1_deposit_index,
        validators,
        balances,
        start_shard,
        randao_mixes,
        active_index_roots,
        compact_committees_roots,
        slashings,
        previous_epoch_attestations,
        current_epoch_attestations,
        previous_crosslinks,
        current_crosslinks,
        justification_bits,
        previous_justified_checkpoint,
        current_justified_checkpoint,
        finalized_checkpoint);
  }

  public BeaconStateWithCache(BeaconStateWithCache state) {
    // Versioning
    this.genesis_time = state.getGenesis_time();
    this.slot = state.getSlot();
    this.fork = new Fork(state.getFork());

    // History
    this.latest_block_header = new BeaconBlockHeader(state.getLatest_block_header());
    this.block_roots = new SSZVector<>(state.getBlock_roots());
    this.state_roots = new SSZVector<>(state.getState_roots());
    this.historical_roots = new SSZList<>(state.getHistorical_roots());

    // Eth1
    this.eth1_data = new Eth1Data(state.getEth1_data());
    this.eth1_data_votes = new SSZList<>(state.getEth1_data_votes());
    this.eth1_deposit_index = state.getEth1_deposit_index();

    // Registry
    this.validators = new SSZList<>(state.getValidators());
    this.balances = new SSZList<>(state.getBalances());

    // Shuffling
    this.start_shard = state.getStart_shard();
    this.randao_mixes = new SSZVector<>(state.getRandao_mixes());
    this.active_index_roots = new SSZVector<>(state.getActive_index_roots());
    this.compact_committees_roots = new SSZVector<>(state.getCompact_committees_roots());

    // Slashings
    this.slashings = new SSZVector<>(state.getSlashings());

    // Attestations
    this.previous_epoch_attestations = new SSZList<>(state.getPrevious_epoch_attestations());
    this.current_epoch_attestations = new SSZList<>(state.getCurrent_epoch_attestations());

    // Crosslinks
    this.current_crosslinks = new SSZVector<>(state.getCurrent_crosslinks());
    this.previous_crosslinks = new SSZVector<>(state.getPrevious_crosslinks());

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

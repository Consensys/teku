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

package tech.pegasys.artemis.api.schema;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;

public class BeaconState {
  public final UnsignedLong genesis_time;
  public final UnsignedLong slot;
  public final Fork fork;
  public final BeaconBlockHeader latest_block_header;
  public final List<Bytes32> block_roots;
  public final List<Bytes32> state_roots;
  public final List<Bytes32> historical_roots;
  public final Eth1Data eth1_data;
  public final List<Eth1Data> eth1_data_votes;
  public final UnsignedLong eth1_deposit_index;
  public final List<Validator> validators;
  public final List<UnsignedLong> balances;
  public final List<Bytes32> randao_mixes;
  public final List<UnsignedLong> slashings;
  public final List<PendingAttestation> previous_epoch_attestations;
  public final List<PendingAttestation> current_epoch_attestations;
  public final Bitvector justification_bits;
  public final Checkpoint previous_justified_checkpoint;
  public final Checkpoint current_justified_checkpoint;
  public final Checkpoint finalized_checkpoint;

  public BeaconState(final tech.pegasys.artemis.datastructures.state.BeaconState beaconState) {
    this.genesis_time = beaconState.getGenesis_time();
    this.slot = beaconState.getSlot();
    this.fork = new Fork(beaconState.getFork());
    this.latest_block_header = new BeaconBlockHeader(beaconState.getLatest_block_header());
    this.block_roots = beaconState.getBlock_roots().stream().collect(Collectors.toList());
    this.state_roots = beaconState.getState_roots().stream().collect(Collectors.toList());
    this.historical_roots = beaconState.getHistorical_roots().stream().collect(Collectors.toList());
    this.eth1_data = new Eth1Data(beaconState.getEth1_data());
    this.eth1_data_votes =
        beaconState.getEth1_data_votes().stream().map(Eth1Data::new).collect(Collectors.toList());
    this.eth1_deposit_index = beaconState.getEth1_deposit_index();
    this.validators =
        beaconState.getValidators().stream().map(Validator::new).collect(Collectors.toList());
    this.balances = beaconState.getBalances().stream().collect(Collectors.toList());
    this.randao_mixes = beaconState.getRandao_mixes().stream().collect(Collectors.toList());
    this.slashings = beaconState.getSlashings().stream().collect(Collectors.toList());
    this.previous_epoch_attestations =
        beaconState.getPrevious_epoch_attestations().stream()
            .map(PendingAttestation::new)
            .collect(Collectors.toList());
    this.current_epoch_attestations =
        beaconState.getCurrent_epoch_attestations().stream()
            .map(PendingAttestation::new)
            .collect(Collectors.toList());
    this.justification_bits = beaconState.getJustification_bits();
    this.previous_justified_checkpoint =
        new Checkpoint(beaconState.getPrevious_justified_checkpoint());
    this.current_justified_checkpoint =
        new Checkpoint(beaconState.getCurrent_justified_checkpoint());
    this.finalized_checkpoint = new Checkpoint(beaconState.getFinalized_checkpoint());
  }
}

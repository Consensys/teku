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

package tech.pegasys.artemis.beaconrestapi.schema;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.state.BeaconState;

public class BeaconStateResponse {

  public UnsignedLong genesis_time;
  public UnsignedLong slot;
  public Fork fork;

  public BeaconBlockHeader latest_block_header;
  public List<Bytes32> block_roots;
  public List<Bytes32> state_roots;
  public List<Bytes32> historical_roots;

  public Eth1Data eth1_data;
  public List<Eth1Data> eth1_data_votes;
  public UnsignedLong eth1_deposit_index;

  public List<Validator> validators;
  public List<UnsignedLong> balances;

  public List<Bytes32> randao_mixes;

  public List<UnsignedLong> slashings;

  public List<PendingAttestation> previous_epoch_attestations;
  public List<PendingAttestation> current_epoch_attestations;

  public String justification_bits;
  public Checkpoint previous_justified_checkpoint;
  public Checkpoint current_justified_checkpoint;
  public Checkpoint finalized_checkpoint;

  public BeaconStateResponse(BeaconState beaconState) {
    this.genesis_time = beaconState.getGenesis_time();
    this.slot = beaconState.getSlot();
    this.fork = new Fork(beaconState.getFork());
    this.latest_block_header = new BeaconBlockHeader(beaconState.getLatest_block_header());
    this.block_roots = beaconState.getBlock_roots();
    this.state_roots = beaconState.getState_roots();
    this.historical_roots = beaconState.getHistorical_roots();

    this.eth1_data = new Eth1Data(beaconState.getEth1_data());
    this.eth1_data_votes =
        beaconState.getEth1_data_votes().stream().map(Eth1Data::new).collect(toUnmodifiableList());
    this.eth1_deposit_index = beaconState.getEth1_deposit_index();

    this.validators =
        beaconState.getValidators().stream().map(Validator::new).collect(toUnmodifiableList());
    this.balances = beaconState.getBalances();
    this.randao_mixes = beaconState.getRandao_mixes();
    this.slashings = beaconState.getSlashings();
    this.previous_epoch_attestations =
        beaconState.getPrevious_epoch_attestations().stream()
            .map(PendingAttestation::new)
            .collect(toUnmodifiableList());
    this.current_epoch_attestations =
        beaconState.getCurrent_epoch_attestations().stream()
            .map(PendingAttestation::new)
            .collect(toUnmodifiableList());

    this.justification_bits = beaconState.getJustification_bits().toString();
    this.previous_justified_checkpoint =
        new Checkpoint(beaconState.getPrevious_justified_checkpoint());
    this.current_justified_checkpoint =
        new Checkpoint(beaconState.getCurrent_justified_checkpoint());
    this.finalized_checkpoint = new Checkpoint(beaconState.getFinalized_checkpoint());
  }
}

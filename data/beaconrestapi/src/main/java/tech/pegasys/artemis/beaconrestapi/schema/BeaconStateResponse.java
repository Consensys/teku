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

import java.util.ArrayList;

@SuppressWarnings("UnusedVariable")
public class BeaconStateResponse {

  public Long genesis_time;
  public Long slot;
  public Fork fork; // For versioning hard forks

  public BeaconBlockHeader latest_block_header;
  public ArrayList<String> block_roots;
  public ArrayList<String> state_roots;
  public ArrayList<String> historical_roots;

  public Eth1Data eth1_data;
  public ArrayList<Eth1Data> eth1_data_votes;
  public Long eth1_deposit_index;

  public ArrayList<Validator> validators;
  public ArrayList<Long> balances;

  public ArrayList<String> randao_mixes;

  public ArrayList<Long> slashings;

  public ArrayList<PendingAttestation> previous_epoch_attestations;
  public ArrayList<PendingAttestation> current_epoch_attestations;

  public String justification_bits;
  public Checkpoint previous_justified_checkpoint;
  public Checkpoint current_justified_checkpoint;
  public Checkpoint finalized_checkpoint;
}

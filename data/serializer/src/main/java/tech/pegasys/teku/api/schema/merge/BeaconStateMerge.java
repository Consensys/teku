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

package tech.pegasys.teku.api.schema.merge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BeaconBlockHeader;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.Checkpoint;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.PendingAttestation;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.api.schema.interfaces.State;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.ssz.collections.SszBitvector;

public class BeaconStateMerge extends BeaconState implements State {
  public final List<PendingAttestation> previous_epoch_attestations;
  public final List<PendingAttestation> current_epoch_attestations;
  public final ExecutionPayloadHeader latest_execution_payload_header;

  @JsonCreator
  public BeaconStateMerge(
      @JsonProperty("genesis_time") final UInt64 genesis_time,
      @JsonProperty("genesis_validators_root") final Bytes32 genesis_validators_root,
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("fork") final Fork fork,
      @JsonProperty("latest_block_header") final BeaconBlockHeader latest_block_header,
      @JsonProperty("block_roots") final List<Bytes32> block_roots,
      @JsonProperty("state_roots") final List<Bytes32> state_roots,
      @JsonProperty("historical_roots") final List<Bytes32> historical_roots,
      @JsonProperty("eth1_data") final Eth1Data eth1_data,
      @JsonProperty("eth1_data_votes") final List<Eth1Data> eth1_data_votes,
      @JsonProperty("eth1_deposit_index") final UInt64 eth1_deposit_index,
      @JsonProperty("validators") final List<Validator> validators,
      @JsonProperty("balances") final List<UInt64> balances,
      @JsonProperty("randao_mixes") final List<Bytes32> randao_mixes,
      @JsonProperty("slashings") final List<UInt64> slashings,
      @JsonProperty("previous_epoch_attestations")
          final List<PendingAttestation> previous_epoch_attestations,
      @JsonProperty("current_epoch_attestations")
          final List<PendingAttestation> current_epoch_attestations,
      @JsonProperty("justification_bits") final SszBitvector justification_bits,
      @JsonProperty("previous_justified_checkpoint") final Checkpoint previous_justified_checkpoint,
      @JsonProperty("current_justified_checkpoint") final Checkpoint current_justified_checkpoint,
      @JsonProperty("finalized_checkpoint") final Checkpoint finalized_checkpoint,
      @JsonProperty("latest_execution_payload_header")
          final ExecutionPayloadHeader latest_execution_payload_header) {
    super(
        genesis_time,
        genesis_validators_root,
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
        randao_mixes,
        slashings,
        justification_bits,
        previous_justified_checkpoint,
        current_justified_checkpoint,
        finalized_checkpoint);
    this.previous_epoch_attestations = previous_epoch_attestations;
    this.current_epoch_attestations = current_epoch_attestations;
    this.latest_execution_payload_header = latest_execution_payload_header;
  }

  public BeaconStateMerge(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState beaconState) {
    super(beaconState);
    // Optionally set merge-specific versioned fields
    final Optional<
            tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge>
        maybeMergeState = beaconState.toVersionMerge();
    if (maybeMergeState.isPresent()) {
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge
          mergeState = maybeMergeState.get();
      this.previous_epoch_attestations =
          mergeState.getPrevious_epoch_attestations().stream()
              .map(PendingAttestation::new)
              .collect(Collectors.toList());
      this.current_epoch_attestations =
          mergeState.getCurrent_epoch_attestations().stream()
              .map(PendingAttestation::new)
              .collect(Collectors.toList());
      this.latest_execution_payload_header =
          new ExecutionPayloadHeader(mergeState.getLatest_execution_payload_header());
    } else {
      this.previous_epoch_attestations = null;
      this.current_epoch_attestations = null;
      this.latest_execution_payload_header = null;
    }
  }

  @Override
  protected void applyAdditionalFields(final MutableBeaconState state) {
    state
        .toMutableVersionMerge()
        .ifPresent(
            mergeState -> {
              mergeState
                  .getPrevious_epoch_attestations()
                  .setAll(
                      previous_epoch_attestations.stream()
                          .map(PendingAttestation::asInternalPendingAttestation)
                          .collect(Collectors.toList()));
              mergeState
                  .getCurrent_epoch_attestations()
                  .setAll(
                      current_epoch_attestations.stream()
                          .map(PendingAttestation::asInternalPendingAttestation)
                          .collect(Collectors.toList()));
              mergeState.setLatestExecutionPayloadHeader(
                  new tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader(
                      latest_execution_payload_header.block_hash,
                      latest_execution_payload_header.parent_hash,
                      latest_execution_payload_header.miner,
                      latest_execution_payload_header.state_root,
                      latest_execution_payload_header.number,
                      latest_execution_payload_header.gas_limit,
                      latest_execution_payload_header.gas_used,
                      latest_execution_payload_header.timestamp,
                      latest_execution_payload_header.receipt_root,
                      latest_execution_payload_header.logs_bloom,
                      latest_execution_payload_header.transactions_root));
            });
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final BeaconStateMerge that = (BeaconStateMerge) o;
    return Objects.equals(previous_epoch_attestations, that.previous_epoch_attestations)
        && Objects.equals(current_epoch_attestations, that.current_epoch_attestations)
        && Objects.equals(latest_execution_payload_header, that.latest_execution_payload_header);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        previous_epoch_attestations, current_epoch_attestations, latest_execution_payload_header);
  }
}

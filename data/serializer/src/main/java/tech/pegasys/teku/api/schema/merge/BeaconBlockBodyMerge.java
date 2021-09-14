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

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlockBody;
import tech.pegasys.teku.api.schema.Deposit;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.spec.SpecVersion;

public class BeaconBlockBodyMerge extends BeaconBlockBody {
  @JsonProperty("execution_payload")
  public final ExecutionPayload executionPayload;

  @JsonCreator
  public BeaconBlockBodyMerge(
      @JsonProperty("randao_reveal") final BLSSignature randao_reveal,
      @JsonProperty("eth1_data") final Eth1Data eth1_data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposer_slashings") final List<ProposerSlashing> proposer_slashings,
      @JsonProperty("attester_slashings") final List<AttesterSlashing> attester_slashings,
      @JsonProperty("attestations") final List<Attestation> attestations,
      @JsonProperty("deposits") final List<Deposit> deposits,
      @JsonProperty("voluntary_exits") final List<SignedVoluntaryExit> voluntary_exits,
      @JsonProperty("execution_payload") final ExecutionPayload execution_payload) {
    super(
        randao_reveal,
        eth1_data,
        graffiti,
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits);
    checkNotNull(execution_payload, "Execution Payload is required for merge blocks");
    this.executionPayload = execution_payload;
  }

  public BeaconBlockBodyMerge(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge
              .BeaconBlockBodyMerge
          message) {
    super(message);
    checkNotNull(message.getExecution_payload(), "Execution Payload is required for merge blocks");
    this.executionPayload = new ExecutionPayload(message.getExecution_payload());
  }

  @Override
  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody
      asInternalBeaconBlockBody(final SpecVersion spec) {
    return super.asInternalBeaconBlockBody(
        spec,
        (builder) ->
            builder.executionPayload(
                () ->
                    new tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload(
                        executionPayload.parent_hash,
                        executionPayload.miner,
                        executionPayload.state_root,
                        executionPayload.receipt_root,
                        executionPayload.logs_bloom,
                        executionPayload.random,
                        executionPayload.block_number,
                        executionPayload.gas_limit,
                        executionPayload.gas_used,
                        executionPayload.timestamp,
                        executionPayload.base_fee_per_gas,
                        executionPayload.block_hash,
                        executionPayload.transactions)));
  }
}

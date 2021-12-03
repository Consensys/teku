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
import tech.pegasys.teku.api.schema.Deposit;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.api.schema.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.api.schema.altair.SyncAggregate;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge.BeaconBlockBodySchemaMerge;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;

public class BeaconBlockBodyMerge extends BeaconBlockBodyAltair {
  @JsonProperty("execution_payload")
  public final ExecutionPayload executionPayload;

  @JsonCreator
  public BeaconBlockBodyMerge(
      @JsonProperty("randao_reveal") final BLSSignature randaoReveal,
      @JsonProperty("eth1_data") final Eth1Data eth1Data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposer_slashings") final List<ProposerSlashing> proposerSlashings,
      @JsonProperty("attester_slashings") final List<AttesterSlashing> attesterSlashings,
      @JsonProperty("attestations") final List<Attestation> attestations,
      @JsonProperty("deposits") final List<Deposit> deposits,
      @JsonProperty("voluntary_exits") final List<SignedVoluntaryExit> voluntaryExits,
      @JsonProperty("sync_aggregate") final SyncAggregate syncAggregate,
      @JsonProperty("execution_payload") final ExecutionPayload executionPayload) {
    super(
        randaoReveal,
        eth1Data,
        graffiti,
        proposerSlashings,
        attesterSlashings,
        attestations,
        deposits,
        voluntaryExits,
        syncAggregate);
    checkNotNull(executionPayload, "Execution Payload is required for merge blocks");
    this.executionPayload = executionPayload;
  }

  public BeaconBlockBodyMerge(
      tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge.BeaconBlockBodyMerge
          message) {
    super(message);
    checkNotNull(message.getExecutionPayload(), "Execution Payload is required for merge blocks");
    this.executionPayload = new ExecutionPayload(message.getExecutionPayload());
  }

  @Override
  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody
      asInternalBeaconBlockBody(final SpecVersion spec) {

    BeaconBlockBodySchemaMerge<?> schema =
        (BeaconBlockBodySchemaMerge<?>) spec.getSchemaDefinitions().getBeaconBlockBodySchema();
    ExecutionPayloadSchema executionPayloadSchema = schema.getExecutionPayloadSchema();

    return super.asInternalBeaconBlockBody(
        spec,
        (builder) ->
            builder.executionPayload(
                () ->
                    executionPayloadSchema.create(
                        executionPayload.parentHash,
                        executionPayload.feeRecipient,
                        executionPayload.stateRoot,
                        executionPayload.receiptRoot,
                        executionPayload.logsBloom,
                        executionPayload.random,
                        executionPayload.blockNumber,
                        executionPayload.gasLimit,
                        executionPayload.gasUsed,
                        executionPayload.timestamp,
                        executionPayload.extraData,
                        executionPayload.baseFeePerGas,
                        executionPayload.blockHash,
                        executionPayload.transactions)));
  }
}

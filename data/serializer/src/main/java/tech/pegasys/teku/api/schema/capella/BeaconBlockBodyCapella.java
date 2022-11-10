/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.api.schema.capella;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadSchemaCapella;

public class BeaconBlockBodyCapella extends BeaconBlockBodyAltair {

  @JsonProperty("execution_payload")
  public final ExecutionPayloadCapella executionPayload;

  @JsonProperty("bls_to_execution_changes")
  public final List<SignedBlsToExecutionChange> blsToExecutionChanges;

  @JsonCreator
  public BeaconBlockBodyCapella(
      @JsonProperty("randao_reveal") final BLSSignature randaoReveal,
      @JsonProperty("eth1_data") final Eth1Data eth1Data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposer_slashings") final List<ProposerSlashing> proposerSlashings,
      @JsonProperty("attester_slashings") final List<AttesterSlashing> attesterSlashings,
      @JsonProperty("attestations") final List<Attestation> attestations,
      @JsonProperty("deposits") final List<Deposit> deposits,
      @JsonProperty("voluntary_exits") final List<SignedVoluntaryExit> voluntaryExits,
      @JsonProperty("sync_aggregate") final SyncAggregate syncAggregate,
      @JsonProperty("execution_payload") final ExecutionPayloadCapella executionPayload,
      @JsonProperty("bls_to_execution_changes")
          final List<SignedBlsToExecutionChange> blsToExecutionChanges) {
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
    checkNotNull(executionPayload, "Execution Payload is required for capella blocks");
    this.executionPayload = executionPayload;
    checkNotNull(blsToExecutionChanges, "BlsToExecutionChange is required for capella blocks");
    this.blsToExecutionChanges = blsToExecutionChanges;
  }

  public BeaconBlockBodyCapella(
      tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyCapella
          message) {
    super(message);
    checkNotNull(message.getExecutionPayload(), "Execution Payload is required for capella blocks");
    this.executionPayload = new ExecutionPayloadCapella(message.getExecutionPayload());
    checkNotNull(
        message.getBlsToExecutionChanges(), "BlsToExecutionChange is required for capella blocks");
    this.blsToExecutionChanges =
        message.getBlsToExecutionChanges().stream()
            .map(SignedBlsToExecutionChange::new)
            .collect(Collectors.toList());
  }

  @Override
  public BeaconBlockBodySchemaCapella<?> getBeaconBlockBodySchema(final SpecVersion spec) {
    return (BeaconBlockBodySchemaCapella<?>) spec.getSchemaDefinitions().getBeaconBlockBodySchema();
  }

  @Override
  public BeaconBlockBody asInternalBeaconBlockBody(final SpecVersion spec) {

    final ExecutionPayloadSchemaCapella executionPayloadSchemaCapella =
        getBeaconBlockBodySchema(spec).getExecutionPayloadSchema().toVersionCapella().orElseThrow();

    final SszListSchema<
            tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange, ?>
        blsToExecutionChangesSchema =
            getBeaconBlockBodySchema(spec).getBlsToExecutionChangesSchema();

    return super.asInternalBeaconBlockBody(
        spec,
        (builder) -> {
          builder.executionPayload(
              () ->
                  SafeFuture.completedFuture(
                      executionPayloadSchemaCapella.create(
                          executionPayload.parentHash,
                          executionPayload.feeRecipient,
                          executionPayload.stateRoot,
                          executionPayload.receiptsRoot,
                          executionPayload.logsBloom,
                          executionPayload.prevRandao,
                          executionPayload.blockNumber,
                          executionPayload.gasLimit,
                          executionPayload.gasUsed,
                          executionPayload.timestamp,
                          executionPayload.extraData,
                          executionPayload.baseFeePerGas,
                          executionPayload.blockHash,
                          executionPayload.transactions,
                          executionPayload.withdrawals)));
          builder.blsToExecutionChanges(
              () ->
                  this.blsToExecutionChanges.stream()
                      .map(b -> b.asInternalSignedBlsToExecutionChange(spec))
                      .collect(blsToExecutionChangesSchema.collector()));
        });
  }
}

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

package tech.pegasys.teku.api.schema.eip4844;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.Deposit;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.api.schema.KZGCommitment;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.api.schema.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.api.schema.altair.SyncAggregate;
import tech.pegasys.teku.api.schema.capella.SignedBlsToExecutionChange;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.BeaconBlockBodySchemaEip4844;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.ExecutionPayloadSchemaEip4844;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public class BeaconBlockBodyEip4844 extends BeaconBlockBodyAltair {

  @JsonProperty("execution_payload")
  public final ExecutionPayloadEip4844 executionPayload;

  @JsonProperty("bls_to_execution_changes")
  public final List<SignedBlsToExecutionChange> blsToExecutionChanges;

  @JsonProperty("blob_kzg_commitments")
  public final List<KZGCommitment> blobKZGCommitments;

  @JsonCreator
  public BeaconBlockBodyEip4844(
      @JsonProperty("randao_reveal") final BLSSignature randaoReveal,
      @JsonProperty("eth1_data") final Eth1Data eth1Data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposer_slashings") final List<ProposerSlashing> proposerSlashings,
      @JsonProperty("attester_slashings") final List<AttesterSlashing> attesterSlashings,
      @JsonProperty("attestations") final List<Attestation> attestations,
      @JsonProperty("deposits") final List<Deposit> deposits,
      @JsonProperty("voluntary_exits") final List<SignedVoluntaryExit> voluntaryExits,
      @JsonProperty("sync_aggregate") final SyncAggregate syncAggregate,
      @JsonProperty("execution_payload") final ExecutionPayloadEip4844 executionPayload,
      @JsonProperty("bls_to_execution_changes")
          final List<SignedBlsToExecutionChange> blsToExecutionChanges,
      @JsonProperty("blob_kzg_commitments") final List<KZGCommitment> blobKZGCommitments) {
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
    checkNotNull(executionPayload, "Execution Payload is required for EIP-4844 blocks");
    this.executionPayload = executionPayload;
    checkNotNull(blsToExecutionChanges, "BlsToExecutionChanges is required for EIP-4844 blocks");
    this.blsToExecutionChanges = blsToExecutionChanges;
    checkNotNull(blobKZGCommitments, "blobKZGCommitments is required for EIP-4844 blocks");
    this.blobKZGCommitments = blobKZGCommitments;
  }

  public BeaconBlockBodyEip4844(
      tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.BeaconBlockBodyEip4844
          message) {
    super(message);
    checkNotNull(
        message.getExecutionPayload(), "Execution Payload is required for EIP-4844 blocks");
    this.executionPayload = new ExecutionPayloadEip4844(message.getExecutionPayload());
    checkNotNull(
        message.getBlsToExecutionChanges(),
        "BlsToExecutionChanges are required for EIP-4844 blocks");
    this.blsToExecutionChanges =
        message.getBlsToExecutionChanges().stream()
            .map(SignedBlsToExecutionChange::new)
            .collect(toList());
    checkNotNull(
        message.getBlobKzgCommitments(), "BlobKzgCommitments are required for EIP-4844 blocks");
    this.blobKZGCommitments =
        message.getBlobKzgCommitments().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(KZGCommitment::new)
            .collect(toList());
  }

  @Override
  public BeaconBlockBodySchemaEip4844<?> getBeaconBlockBodySchema(final SpecVersion spec) {
    return (BeaconBlockBodySchemaEip4844<?>) spec.getSchemaDefinitions().getBeaconBlockBodySchema();
  }

  @Override
  public BeaconBlockBody asInternalBeaconBlockBody(final SpecVersion spec) {

    final ExecutionPayloadSchemaEip4844 executionPayloadSchemaEip4844 =
        getBeaconBlockBodySchema(spec).getExecutionPayloadSchema().toVersionEip4844().orElseThrow();

    final SszListSchema<
            tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange, ?>
        blsToExecutionChangesSchema =
            getBeaconBlockBodySchema(spec).getBlsToExecutionChangesSchema();
    final SszListSchema<SszKZGCommitment, ?> blobKZGCommitmentsSchema =
        getBeaconBlockBodySchema(spec).getBlobKzgCommitmentsSchema();
    return super.asInternalBeaconBlockBody(
        spec,
        (builder) -> {
          builder.executionPayload(
              () ->
                  SafeFuture.completedFuture(
                      executionPayloadSchemaEip4844.create(
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
                          executionPayload.excessBlobs,
                          executionPayload.blockHash,
                          executionPayload.transactions,
                          executionPayload.withdrawals.stream()
                              .map(
                                  withdrawal ->
                                      withdrawal.asInternalWithdrawal(
                                          executionPayloadSchemaEip4844.getWithdrawalSchema()))
                              .collect(toList()))));
          builder.blsToExecutionChanges(
              () ->
                  this.blsToExecutionChanges.stream()
                      .map(b -> b.asInternalSignedBlsToExecutionChange(spec))
                      .collect(blsToExecutionChangesSchema.collector()));
          builder.blobKzgCommitments(
              () ->
                  this.blobKZGCommitments.stream()
                      .map(KZGCommitment::asInternalKZGCommitment)
                      .map(SszKZGCommitment::new)
                      .collect(blobKZGCommitmentsSchema.collector()));
        });
  }
}

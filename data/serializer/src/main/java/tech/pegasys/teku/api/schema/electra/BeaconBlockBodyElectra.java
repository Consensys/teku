/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.api.schema.electra;

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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodySchemaElectra;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public class BeaconBlockBodyElectra extends BeaconBlockBodyAltair {

  @JsonProperty("execution_payload")
  public final ExecutionPayloadElectra executionPayload;

  @JsonProperty("bls_to_execution_changes")
  public final List<SignedBlsToExecutionChange> blsToExecutionChanges;

  @JsonProperty("blob_kzg_commitments")
  public final List<KZGCommitment> blobKZGCommitments;

  @JsonProperty("consolidations")
  public final List<SignedConsolidation> consolidations;

  @JsonCreator
  public BeaconBlockBodyElectra(
      @JsonProperty("randao_reveal") final BLSSignature randaoReveal,
      @JsonProperty("eth1_data") final Eth1Data eth1Data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposer_slashings") final List<ProposerSlashing> proposerSlashings,
      @JsonProperty("attester_slashings") final List<AttesterSlashing> attesterSlashings,
      @JsonProperty("attestations") final List<Attestation> attestations,
      @JsonProperty("deposits") final List<Deposit> deposits,
      @JsonProperty("voluntary_exits") final List<SignedVoluntaryExit> voluntaryExits,
      @JsonProperty("sync_aggregate") final SyncAggregate syncAggregate,
      @JsonProperty("execution_payload") final ExecutionPayloadElectra executionPayload,
      @JsonProperty("bls_to_execution_changes")
          final List<SignedBlsToExecutionChange> blsToExecutionChanges,
      @JsonProperty("blob_kzg_commitments") final List<KZGCommitment> blobKZGCommitments,
      @JsonProperty("consolidations") final List<SignedConsolidation> consolidations) {
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
    checkNotNull(executionPayload, "Execution Payload is required for Electra blocks");
    this.executionPayload = executionPayload;
    checkNotNull(blsToExecutionChanges, "BlsToExecutionChanges is required for Electra blocks");
    this.blsToExecutionChanges = blsToExecutionChanges;
    checkNotNull(blobKZGCommitments, "blobKZGCommitments is required for Electra blocks");
    this.blobKZGCommitments = blobKZGCommitments;
    this.consolidations = consolidations;
  }

  public BeaconBlockBodyElectra(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra
              .BeaconBlockBodyElectra
          message) {
    super(message);
    checkNotNull(message.getExecutionPayload(), "Execution Payload is required for Electra blocks");
    this.executionPayload = new ExecutionPayloadElectra(message.getExecutionPayload());
    checkNotNull(
        message.getBlsToExecutionChanges(),
        "BlsToExecutionChanges are required for Electra blocks");
    this.blsToExecutionChanges =
        message.getBlsToExecutionChanges().stream().map(SignedBlsToExecutionChange::new).toList();
    checkNotNull(
        message.getBlobKzgCommitments(), "BlobKzgCommitments are required for Electra blocks");
    this.blobKZGCommitments =
        message.getBlobKzgCommitments().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(KZGCommitment::new)
            .toList();
    this.consolidations =
        message.getConsolidations().stream()
            .map(SignedConsolidation::new)
            .collect(Collectors.toList());
  }

  @Override
  public BeaconBlockBodySchemaElectra<?> getBeaconBlockBodySchema(final SpecVersion spec) {
    return (BeaconBlockBodySchemaElectra<?>) spec.getSchemaDefinitions().getBeaconBlockBodySchema();
  }

  @Override
  public BeaconBlockBody asInternalBeaconBlockBody(final SpecVersion spec) {
    final SszListSchema<
            tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange, ?>
        blsToExecutionChangesSchema =
            getBeaconBlockBodySchema(spec).getBlsToExecutionChangesSchema();
    final SszListSchema<SszKZGCommitment, ?> blobKZGCommitmentsSchema =
        getBeaconBlockBodySchema(spec).getBlobKzgCommitmentsSchema();
    final SszListSchema<tech.pegasys.teku.spec.datastructures.consolidations.SignedConsolidation, ?>
        signedConsolidationSchema = getBeaconBlockBodySchema(spec).getConsolidationsSchema();
    return super.asInternalBeaconBlockBody(
        spec,
        builder -> {
          builder.executionPayload(executionPayload.asInternalExecutionPayload(spec));
          builder.blsToExecutionChanges(
              this.blsToExecutionChanges.stream()
                  .map(b -> b.asInternalSignedBlsToExecutionChange(spec))
                  .collect(blsToExecutionChangesSchema.collector()));
          builder.blobKzgCommitments(
              this.blobKZGCommitments.stream()
                  .map(KZGCommitment::asInternalKZGCommitment)
                  .map(SszKZGCommitment::new)
                  .collect(blobKZGCommitmentsSchema.collector()));
          builder.consolidations(
              this.consolidations.stream()
                  .map(b -> b.asInternalSignedConsolidation(spec))
                  .collect(signedConsolidationSchema.collector()));
          return SafeFuture.COMPLETE;
        });
  }
}
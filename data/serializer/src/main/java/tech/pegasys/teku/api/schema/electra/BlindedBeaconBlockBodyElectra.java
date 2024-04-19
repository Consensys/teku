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
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.api.schema.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.api.schema.altair.SyncAggregate;
import tech.pegasys.teku.api.schema.capella.SignedBlsToExecutionChange;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.KZGCommitment;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BlindedBeaconBlockBodySchemaElectra;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;

public class BlindedBeaconBlockBodyElectra extends BeaconBlockBodyAltair {

  @JsonProperty("execution_payload_header")
  public final ExecutionPayloadHeaderElectra executionPayloadHeader;

  @JsonProperty("bls_to_execution_changes")
  public final List<SignedBlsToExecutionChange> blsToExecutionChanges;

  @JsonProperty("blob_kzg_commitments")
  public final List<tech.pegasys.teku.api.schema.KZGCommitment> blobKZGCommitments;

  @JsonProperty("consolidations")
  public final List<SignedConsolidation> consolidations;

  @JsonCreator
  public BlindedBeaconBlockBodyElectra(
      @JsonProperty("randao_reveal") final BLSSignature randaoReveal,
      @JsonProperty("eth1_data") final Eth1Data eth1Data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposer_slashings") final List<ProposerSlashing> proposerSlashings,
      @JsonProperty("attester_slashings") final List<AttesterSlashing> attesterSlashings,
      @JsonProperty("attestations") final List<Attestation> attestations,
      @JsonProperty("deposits") final List<Deposit> deposits,
      @JsonProperty("voluntary_exits") final List<SignedVoluntaryExit> voluntaryExits,
      @JsonProperty("sync_aggregate") final SyncAggregate syncAggregate,
      @JsonProperty("execution_payload_header")
          final ExecutionPayloadHeaderElectra executionPayloadHeader,
      @JsonProperty("bls_to_execution_changes")
          final List<SignedBlsToExecutionChange> blsToExecutionChanges,
      @JsonProperty("blob_kzg_commitments")
          final List<tech.pegasys.teku.api.schema.KZGCommitment> blobKZGCommitments,
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
    checkNotNull(
        executionPayloadHeader, "Execution Payload Header is required for Electra blinded blocks");
    this.executionPayloadHeader = executionPayloadHeader;
    checkNotNull(
        blsToExecutionChanges, "blsToExecutionChanges is required for Electra blinded blocks");
    this.blsToExecutionChanges = blsToExecutionChanges;
    checkNotNull(blobKZGCommitments, "blobKZGCommitments is required for Electra blinded blocks");
    this.blobKZGCommitments = blobKZGCommitments;

    this.consolidations = consolidations;
  }

  public BlindedBeaconBlockBodyElectra(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra
              .BlindedBeaconBlockBodyElectra
          blockBody) {
    super(blockBody);
    this.executionPayloadHeader =
        new ExecutionPayloadHeaderElectra(blockBody.getExecutionPayloadHeader());
    this.blsToExecutionChanges =
        blockBody.getBlsToExecutionChanges().stream().map(SignedBlsToExecutionChange::new).toList();
    this.blobKZGCommitments =
        blockBody.getBlobKzgCommitments().stream()
            .map(tech.pegasys.teku.api.schema.KZGCommitment::new)
            .toList();
    this.consolidations =
        blockBody.getConsolidations().stream()
            .map(SignedConsolidation::new)
            .collect(Collectors.toList());
  }

  @Override
  public BlindedBeaconBlockBodySchemaElectra<?> getBeaconBlockBodySchema(final SpecVersion spec) {
    return (BlindedBeaconBlockBodySchemaElectra<?>)
        spec.getSchemaDefinitions().getBlindedBeaconBlockBodySchema();
  }

  @Override
  public boolean isBlinded() {
    return true;
  }

  @Override
  public BeaconBlockBody asInternalBeaconBlockBody(final SpecVersion spec) {

    final ExecutionPayloadHeaderSchema<?> executionPayloadHeaderSchema =
        getBeaconBlockBodySchema(spec).getExecutionPayloadHeaderSchema();

    final SszListSchema<
            tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange, ?>
        blsToExecutionChangesSchema = getBeaconBlockBodySchema(spec).getBlsToExecutionChanges();

    final SszListSchema<KZGCommitment, ?> blobKZGCommitmentsSchema =
        getBeaconBlockBodySchema(spec).getBlobKzgCommitmentsSchema();

    final SszListSchema<tech.pegasys.teku.spec.datastructures.consolidations.SignedConsolidation, ?>
        signedConsolidationSchema = getBeaconBlockBodySchema(spec).getConsolidationsSchema();

    return super.asInternalBeaconBlockBody(
        spec,
        builder -> {
          builder.executionPayloadHeader(
              executionPayloadHeader.asInternalExecutionPayloadHeader(
                  executionPayloadHeaderSchema));
          builder.blsToExecutionChanges(
              this.blsToExecutionChanges.stream()
                  .map(b -> b.asInternalSignedBlsToExecutionChange(spec))
                  .collect(blsToExecutionChangesSchema.collector()));
          builder.blobKzgCommitments(
              this.blobKZGCommitments.stream()
                  .map(tech.pegasys.teku.api.schema.KZGCommitment::asInternalKZGCommitment)
                  .collect(blobKZGCommitmentsSchema.collector()));
          builder.consolidations(
              this.consolidations.stream()
                  .map(b -> b.asInternalSignedConsolidation(spec))
                  .collect(signedConsolidationSchema.collector()));
          return SafeFuture.COMPLETE;
        });
  }
}

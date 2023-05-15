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

package tech.pegasys.teku.api.schema.deneb;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.api.schema.Attestation.ATTESTATION_TYPE;
import static tech.pegasys.teku.api.schema.AttesterSlashing.ATTESTER_SLASHING_TYPE;
import static tech.pegasys.teku.api.schema.BLSSignature.BLS_SIGNATURE_TYPE;
import static tech.pegasys.teku.api.schema.Deposit.DEPOSIT_TYPE;
import static tech.pegasys.teku.api.schema.Eth1Data.ETH_1_DATA_TYPE;
import static tech.pegasys.teku.api.schema.KZGCommitment.KZG_COMMITMENT_TYPE;
import static tech.pegasys.teku.api.schema.ProposerSlashing.PROPOSER_SLASHING_TYPE;
import static tech.pegasys.teku.api.schema.SignedVoluntaryExit.SIGNED_VOLUNTARY_EXIT_TYPE;
import static tech.pegasys.teku.api.schema.altair.SyncAggregate.SYNC_AGGREGATE_TYPE;
import static tech.pegasys.teku.api.schema.capella.SignedBlsToExecutionChange.SIGNED_BLS_TO_EXECUTION_CHANGE_TYPE;
import static tech.pegasys.teku.api.schema.deneb.ExecutionPayloadDeneb.EXECUTION_PAYLOAD_DENEB_TYPE;

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
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableListTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodySchemaDeneb;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public class BeaconBlockBodyDeneb extends BeaconBlockBodyAltair {

  @JsonProperty("execution_payload")
  public ExecutionPayloadDeneb executionPayload;

  @JsonProperty("bls_to_execution_changes")
  public List<SignedBlsToExecutionChange> blsToExecutionChanges;

  @JsonProperty("blob_kzg_commitments")
  public List<KZGCommitment> blobKZGCommitments;

  public static final DeserializableTypeDefinition<BeaconBlockBodyDeneb>
      BEACON_BLOCK_DENEB_BODY_TYPE =
          DeserializableTypeDefinition.object(BeaconBlockBodyDeneb.class)
              .initializer(BeaconBlockBodyDeneb::new)
              .withField(
                  "randao_reveal",
                  BLS_SIGNATURE_TYPE,
                  BeaconBlockBodyDeneb::getRandaoReveal,
                  BeaconBlockBodyDeneb::setRandaoReveal)
              .withField(
                  "eth1_data",
                  ETH_1_DATA_TYPE,
                  BeaconBlockBodyDeneb::getEth1Data,
                  BeaconBlockBodyDeneb::setEth1Data)
              .withField(
                  "graffiti",
                  CoreTypes.BYTES32_TYPE,
                  BeaconBlockBodyDeneb::getGraffiti,
                  BeaconBlockBodyDeneb::setGraffiti)
              .withField(
                  "proposer_slashings",
                  new DeserializableListTypeDefinition<>(PROPOSER_SLASHING_TYPE),
                  BeaconBlockBodyDeneb::getProposerSlashings,
                  BeaconBlockBodyDeneb::setProposerSlashings)
              .withField(
                  "attester_slashings",
                  new DeserializableListTypeDefinition<>(ATTESTER_SLASHING_TYPE),
                  BeaconBlockBodyDeneb::getAttesterSlashings,
                  BeaconBlockBodyDeneb::setAttesterSlashings)
              .withField(
                  "attestations",
                  new DeserializableListTypeDefinition<>(ATTESTATION_TYPE),
                  BeaconBlockBodyDeneb::getAttestations,
                  BeaconBlockBodyDeneb::setAttestations)
              .withField(
                  "deposits",
                  new DeserializableListTypeDefinition<>(DEPOSIT_TYPE),
                  BeaconBlockBodyDeneb::getDeposits,
                  tech.pegasys.teku.api.schema.BeaconBlockBody::setDeposits)
              .withField(
                  "voluntary_exits",
                  new DeserializableListTypeDefinition<>(SIGNED_VOLUNTARY_EXIT_TYPE),
                  BeaconBlockBodyDeneb::getVoluntaryExits,
                  BeaconBlockBodyDeneb::setVoluntaryExits)
              .withField(
                  "sync_aggregate",
                  SYNC_AGGREGATE_TYPE,
                  BeaconBlockBodyDeneb::getSyncAggregate,
                  BeaconBlockBodyDeneb::setSyncAggregate)
              .withField(
                  "execution_payload",
                  EXECUTION_PAYLOAD_DENEB_TYPE,
                  BeaconBlockBodyDeneb::getExecutionPayload,
                  BeaconBlockBodyDeneb::setExecutionPayload)
              .withField(
                  "bls_to_execution_changes",
                  new DeserializableListTypeDefinition<>(SIGNED_BLS_TO_EXECUTION_CHANGE_TYPE),
                  BeaconBlockBodyDeneb::getBlsToExecutionChanges,
                  BeaconBlockBodyDeneb::setBlsToExecutionChanges)
              .withField(
                  "blob_kzg_commitments",
                  new DeserializableListTypeDefinition<>(KZG_COMMITMENT_TYPE),
                  BeaconBlockBodyDeneb::getBlobKZGCommitments,
                  BeaconBlockBodyDeneb::setBlobKZGCommitments)
              .build();

  @JsonCreator
  public BeaconBlockBodyDeneb(
      @JsonProperty("randao_reveal") final BLSSignature randaoReveal,
      @JsonProperty("eth1_data") final Eth1Data eth1Data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposer_slashings") final List<ProposerSlashing> proposerSlashings,
      @JsonProperty("attester_slashings") final List<AttesterSlashing> attesterSlashings,
      @JsonProperty("attestations") final List<Attestation> attestations,
      @JsonProperty("deposits") final List<Deposit> deposits,
      @JsonProperty("voluntary_exits") final List<SignedVoluntaryExit> voluntaryExits,
      @JsonProperty("sync_aggregate") final SyncAggregate syncAggregate,
      @JsonProperty("execution_payload") final ExecutionPayloadDeneb executionPayload,
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
    checkNotNull(executionPayload, "Execution Payload is required for Deneb blocks");
    this.executionPayload = executionPayload;
    checkNotNull(blsToExecutionChanges, "BlsToExecutionChanges is required for Deneb blocks");
    this.blsToExecutionChanges = blsToExecutionChanges;
    checkNotNull(blobKZGCommitments, "blobKZGCommitments is required for Deneb blocks");
    this.blobKZGCommitments = blobKZGCommitments;
  }

  public BeaconBlockBodyDeneb(
      tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb
          message) {
    super(message);
    checkNotNull(message.getExecutionPayload(), "Execution Payload is required for Deneb blocks");
    this.executionPayload = new ExecutionPayloadDeneb(message.getExecutionPayload());
    checkNotNull(
        message.getBlsToExecutionChanges(), "BlsToExecutionChanges are required for Deneb blocks");
    this.blsToExecutionChanges =
        message.getBlsToExecutionChanges().stream()
            .map(SignedBlsToExecutionChange::new)
            .collect(toList());
    checkNotNull(
        message.getBlobKzgCommitments(), "BlobKzgCommitments are required for Deneb blocks");
    this.blobKZGCommitments =
        message.getBlobKzgCommitments().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(KZGCommitment::new)
            .collect(toList());
  }

  public BeaconBlockBodyDeneb() {}

  public ExecutionPayloadDeneb getExecutionPayload() {
    return executionPayload;
  }

  public void setExecutionPayload(ExecutionPayloadDeneb executionPayload) {
    this.executionPayload = executionPayload;
  }

  public List<SignedBlsToExecutionChange> getBlsToExecutionChanges() {
    return blsToExecutionChanges;
  }

  public void setBlsToExecutionChanges(List<SignedBlsToExecutionChange> blsToExecutionChanges) {
    this.blsToExecutionChanges = blsToExecutionChanges;
  }

  public List<KZGCommitment> getBlobKZGCommitments() {
    return blobKZGCommitments;
  }

  public void setBlobKZGCommitments(List<KZGCommitment> blobKZGCommitments) {
    this.blobKZGCommitments = blobKZGCommitments;
  }

  @Override
  public BeaconBlockBodySchemaDeneb<?> getBeaconBlockBodySchema(final SpecVersion spec) {
    return (BeaconBlockBodySchemaDeneb<?>) spec.getSchemaDefinitions().getBeaconBlockBodySchema();
  }

  @Override
  public BeaconBlockBody asInternalBeaconBlockBody(final SpecVersion spec) {
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
              SafeFuture.completedFuture(executionPayload.asInternalExecutionPayload(spec)));
          builder.blsToExecutionChanges(
              this.blsToExecutionChanges.stream()
                  .map(b -> b.asInternalSignedBlsToExecutionChange(spec))
                  .collect(blsToExecutionChangesSchema.collector()));
          builder.blobKzgCommitments(
              SafeFuture.completedFuture(
                  this.blobKZGCommitments.stream()
                      .map(KZGCommitment::asInternalKZGCommitment)
                      .map(SszKZGCommitment::new)
                      .collect(blobKZGCommitmentsSchema.collector())));
        });
  }
}

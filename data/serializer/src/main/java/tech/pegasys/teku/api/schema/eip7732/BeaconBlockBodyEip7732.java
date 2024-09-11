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

package tech.pegasys.teku.api.schema.eip7732;

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
import tech.pegasys.teku.api.schema.capella.SignedBlsToExecutionChange;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.BeaconBlockBodySchemaEip7732;

public class BeaconBlockBodyEip7732 extends BeaconBlockBodyAltair {

  @JsonProperty("bls_to_execution_changes")
  public final List<SignedBlsToExecutionChange> blsToExecutionChanges;

  @JsonProperty("signed_execution_payload_header")
  public final SignedExecutionPayloadHeader signedExecutionPayloadHeader;

  @JsonProperty("payload_attestations")
  public final List<PayloadAttestation> payloadAttestations;

  @JsonCreator
  public BeaconBlockBodyEip7732(
      @JsonProperty("randao_reveal") final BLSSignature randaoReveal,
      @JsonProperty("eth1_data") final Eth1Data eth1Data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposer_slashings") final List<ProposerSlashing> proposerSlashings,
      @JsonProperty("attester_slashings") final List<AttesterSlashing> attesterSlashings,
      @JsonProperty("attestations") final List<Attestation> attestations,
      @JsonProperty("deposits") final List<Deposit> deposits,
      @JsonProperty("voluntary_exits") final List<SignedVoluntaryExit> voluntaryExits,
      @JsonProperty("sync_aggregate") final SyncAggregate syncAggregate,
      @JsonProperty("bls_to_execution_changes")
          final List<SignedBlsToExecutionChange> blsToExecutionChanges,
      @JsonProperty("signed_execution_payload_header")
          final SignedExecutionPayloadHeader signedExecutionPayloadHeader,
      @JsonProperty("payload_attestations") final List<PayloadAttestation> payloadAttestations) {
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
    checkNotNull(blsToExecutionChanges, "BlsToExecutionChanges is required for Eip7732 blocks");
    this.blsToExecutionChanges = blsToExecutionChanges;
    checkNotNull(
        signedExecutionPayloadHeader,
        "SignedExecutionPayloadHeader is required for Eip7732 blocks");
    this.signedExecutionPayloadHeader = signedExecutionPayloadHeader;
    checkNotNull(payloadAttestations, "PayloadAttestations is required for Eip7732 blocks");
    this.payloadAttestations = payloadAttestations;
  }

  public BeaconBlockBodyEip7732(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732
              .BeaconBlockBodyEip7732
          message) {
    super(message);
    checkNotNull(
        message.getBlsToExecutionChanges(),
        "BlsToExecutionChanges are required for Eip7732 blocks");
    this.blsToExecutionChanges =
        message.getBlsToExecutionChanges().stream().map(SignedBlsToExecutionChange::new).toList();
    checkNotNull(
        message.getSignedExecutionPayloadHeader(),
        "SignedExecutionPayloadHeader are required for Eip7732 blocks");
    this.signedExecutionPayloadHeader =
        new SignedExecutionPayloadHeader(message.getSignedExecutionPayloadHeader());
    checkNotNull(
        message.getPayloadAttestations(), "PayloadAttestations are required for Eip7732 blocks");
    this.payloadAttestations =
        message.getPayloadAttestations().stream().map(PayloadAttestation::new).toList();
  }

  @Override
  public BeaconBlockBodySchemaEip7732<?> getBeaconBlockBodySchema(final SpecVersion spec) {
    return (BeaconBlockBodySchemaEip7732<?>) spec.getSchemaDefinitions().getBeaconBlockBodySchema();
  }

  @Override
  public BeaconBlockBody asInternalBeaconBlockBody(final SpecVersion spec) {
    final SszListSchema<
            tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange, ?>
        blsToExecutionChangesSchema =
            getBeaconBlockBodySchema(spec).getBlsToExecutionChangesSchema();
    return super.asInternalBeaconBlockBody(
        spec,
        builder -> {
          builder.blsToExecutionChanges(
              this.blsToExecutionChanges.stream()
                  .map(b -> b.asInternalSignedBlsToExecutionChange(spec))
                  .collect(blsToExecutionChangesSchema.collector()));
          builder.signedExecutionPayloadHeader(
              signedExecutionPayloadHeader.asInternalSignedExecutionPayloadHeader(spec));
          final SszListSchema<
                  tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation, ?>
              payloadAttestationsSchema =
                  getBeaconBlockBodySchema(spec).getPayloadAttestationsSchema();
          builder.payloadAttestations(
              this.payloadAttestations.stream()
                  .map(pa -> pa.asInternalPayloadAttestation(spec))
                  .collect(payloadAttestationsSchema.collector()));
          return SafeFuture.COMPLETE;
        });
  }
}

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
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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
import tech.pegasys.teku.api.schema.altair.SyncAggregate;
import tech.pegasys.teku.api.schema.capella.SignedBlsToExecutionChange;
import tech.pegasys.teku.api.schema.deneb.BlindedBeaconBlockBodyDeneb;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BlindedBeaconBlockBodySchemaElectra;

public class BlindedBeaconBlockBodyElectra extends BlindedBeaconBlockBodyDeneb {

  @JsonProperty("execution_requests_root")
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 executionRequestsRoot;

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
      @JsonProperty("blob_kzg_commitments") final List<KZGCommitment> blobKZGCommitments,
      @JsonProperty("execution_requests_root") final Bytes32 executionRequestsRoot) {
    super(
        randaoReveal,
        eth1Data,
        graffiti,
        proposerSlashings,
        attesterSlashings,
        attestations,
        deposits,
        voluntaryExits,
        syncAggregate,
        executionPayloadHeader,
        blsToExecutionChanges,
        blobKZGCommitments);
    checkNotNull(
        executionRequestsRoot, "execution_requests_root is required for Electra blinded blocks");
    this.executionRequestsRoot = executionRequestsRoot;
  }

  public BlindedBeaconBlockBodyElectra(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra
              .BlindedBeaconBlockBodyElectra
          blockBody) {
    super(blockBody);
    // TODO add execution requests from BeaconBlocBodyElectra
    // (https://github.com/Consensys/teku/pull/8600)
    this.executionRequestsRoot = Bytes32.ZERO;
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
    return super.asInternalBeaconBlockBody(
        spec,
        builder -> {
          // TODO add execution requests root (https://github.com/Consensys/teku/pull/8600)
          return SafeFuture.COMPLETE;
        });
  }
}

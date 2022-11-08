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
import tech.pegasys.teku.api.schema.altair.SyncAggregate;
import tech.pegasys.teku.api.schema.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.api.schema.bellatrix.ExecutionPayloadHeader;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BlindedBeaconBlockBodySchemaCapella;

public class BlindedBeaconBlockBodyCapella extends BlindedBeaconBlockBodyBellatrix {

  public final List<BlsToExecutionChange> blsToExecutionChanges;

  @JsonCreator
  public BlindedBeaconBlockBodyCapella(
      @JsonProperty("randao_reveal") final BLSSignature randaoReveal,
      @JsonProperty("eth1_data") final Eth1Data eth1Data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposer_slashings") final List<ProposerSlashing> proposerSlashings,
      @JsonProperty("attester_slashings") final List<AttesterSlashing> attesterSlashings,
      @JsonProperty("attestations") final List<Attestation> attestations,
      @JsonProperty("deposits") final List<Deposit> deposits,
      @JsonProperty("voluntary_exits") final List<SignedVoluntaryExit> voluntaryExits,
      @JsonProperty("sync_aggregate") final SyncAggregate syncAggregate,
      @JsonProperty("execution_payload_header") final ExecutionPayloadHeader executionPayloadHeader,
      @JsonProperty("bls_to_execution_changes")
          final List<BlsToExecutionChange> blsToExecutionChanges) {
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
        executionPayloadHeader);
    checkNotNull(
        blsToExecutionChanges, "Execution Payload Header is required for capella blinded blocks");
    this.blsToExecutionChanges = blsToExecutionChanges;
  }

  public BlindedBeaconBlockBodyCapella(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella
              .BlindedBeaconBlockBodyCapella
          blockBody) {
    super(blockBody);
    this.blsToExecutionChanges =
        blockBody.getBlsToExecutionChanges().stream()
            .map(BlsToExecutionChange::new)
            .collect(Collectors.toList());
    ;
  }

  @Override
  public BlindedBeaconBlockBodySchemaCapella<?> getBeaconBlockBodySchema(final SpecVersion spec) {
    return (BlindedBeaconBlockBodySchemaCapella<?>)
        spec.getSchemaDefinitions().getBlindedBeaconBlockBodySchema();
  }

  @Override
  public boolean isBlinded() {
    return true;
  }

  @Override
  public BeaconBlockBody asInternalBeaconBlockBody(final SpecVersion spec) {
    final SszListSchema<tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange, ?>
        blsToExecutionChangesSchema = getBeaconBlockBodySchema(spec).getBlsToExecutionChanges();

    return super.asInternalBeaconBlockBody(
        spec,
        (builder) ->
            builder.blsToExecutionChanges(
                () ->
                    this.blsToExecutionChanges.stream()
                        .map(b -> b.asInternalBlsToExecutionChange(spec))
                        .collect(blsToExecutionChangesSchema.collector())));
  }
}

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

package tech.pegasys.teku.api.schema.altair;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.function.Consumer;
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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;

public class BeaconBlockBodyAltair extends BeaconBlockBody {
  @JsonProperty("sync_aggregate")
  public final SyncAggregate syncAggregate;

  @JsonCreator
  public BeaconBlockBodyAltair(
      @JsonProperty("randao_reveal") final BLSSignature randao_reveal,
      @JsonProperty("eth1_data") final Eth1Data eth1_data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposer_slashings") final List<ProposerSlashing> proposer_slashings,
      @JsonProperty("attester_slashings") final List<AttesterSlashing> attester_slashings,
      @JsonProperty("attestations") final List<Attestation> attestations,
      @JsonProperty("deposits") final List<Deposit> deposits,
      @JsonProperty("voluntary_exits") final List<SignedVoluntaryExit> voluntary_exits,
      @JsonProperty("sync_aggregate") final SyncAggregate sync_aggregate) {
    super(
        randao_reveal,
        eth1_data,
        graffiti,
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits);
    checkNotNull(sync_aggregate, "Sync Aggregate is required for altair blocks");
    this.syncAggregate = sync_aggregate;
  }

  public BeaconBlockBodyAltair(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair
              .BeaconBlockBodyAltair
          message) {
    super(message);
    this.syncAggregate =
        new tech.pegasys.teku.api.schema.altair.SyncAggregate(message.getSyncAggregate());
    checkNotNull(syncAggregate, "Sync Aggregate is required for altair blocks");
  }

  @Override
  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody
      asInternalBeaconBlockBody(
          final SpecVersion spec, Consumer<BeaconBlockBodyBuilder> builderRef) {
    BeaconBlockBodySchemaAltair<?> schema =
        (BeaconBlockBodySchemaAltair<?>) spec.getSchemaDefinitions().getBeaconBlockBodySchema();
    SyncAggregateSchema syncAggregateSchema = schema.getSyncAggregateSchema();
    return super.asInternalBeaconBlockBody(
        spec,
        (builder) -> {
          builderRef.accept(builder);
          builder.syncAggregate(
              () ->
                  syncAggregateSchema.create(
                      syncAggregateSchema
                          .getSyncCommitteeBitsSchema()
                          .fromBytes(syncAggregate.syncCommitteeBits)
                          .getAllSetBits(),
                      syncAggregate.syncCommitteeSignature.asInternalBLSSignature()));
        });
  }
}

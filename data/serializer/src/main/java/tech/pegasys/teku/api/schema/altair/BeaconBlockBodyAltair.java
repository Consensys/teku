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

package tech.pegasys.teku.api.schema.altair;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.api.schema.Attestation.ATTESTATION_TYPE;
import static tech.pegasys.teku.api.schema.AttesterSlashing.ATTESTER_SLASHING_TYPE;
import static tech.pegasys.teku.api.schema.BLSSignature.BLS_SIGNATURE_TYPE;
import static tech.pegasys.teku.api.schema.Deposit.DEPOSIT_TYPE;
import static tech.pegasys.teku.api.schema.Eth1Data.ETH_1_DATA_TYPE;
import static tech.pegasys.teku.api.schema.ProposerSlashing.PROPOSER_SLASHING_TYPE;
import static tech.pegasys.teku.api.schema.SignedVoluntaryExit.SIGNED_VOLUNTARY_EXIT_TYPE;
import static tech.pegasys.teku.api.schema.altair.SyncAggregate.SYNC_AGGREGATE_TYPE;

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
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableListTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;

@SuppressWarnings("JavaCase")
public class BeaconBlockBodyAltair extends BeaconBlockBody {
  @JsonProperty("sync_aggregate")
  public SyncAggregate syncAggregate;

  public static final DeserializableTypeDefinition<BeaconBlockBodyAltair>
      BEACON_BLOCK_ALTAIR_BODY_TYPE =
          DeserializableTypeDefinition.object(BeaconBlockBodyAltair.class)
              .initializer(BeaconBlockBodyAltair::new)
              .withField(
                  "randao_reveal",
                  BLS_SIGNATURE_TYPE,
                  BeaconBlockBodyAltair::getRandaoReveal,
                  BeaconBlockBodyAltair::setRandaoReveal)
              .withField(
                  "eth1_data",
                  ETH_1_DATA_TYPE,
                  BeaconBlockBodyAltair::getEth1Data,
                  BeaconBlockBodyAltair::setEth1Data)
              .withField(
                  "graffiti",
                  CoreTypes.BYTES32_TYPE,
                  BeaconBlockBodyAltair::getGraffiti,
                  BeaconBlockBodyAltair::setGraffiti)
              .withField(
                  "proposer_slashings",
                  new DeserializableListTypeDefinition<>(PROPOSER_SLASHING_TYPE),
                  BeaconBlockBodyAltair::getProposerSlashings,
                  BeaconBlockBodyAltair::setProposerSlashings)
              .withField(
                  "attester_slashings",
                  new DeserializableListTypeDefinition<>(ATTESTER_SLASHING_TYPE),
                  BeaconBlockBodyAltair::getAttesterSlashings,
                  BeaconBlockBodyAltair::setAttesterSlashings)
              .withField(
                  "attestations",
                  new DeserializableListTypeDefinition<>(ATTESTATION_TYPE),
                  BeaconBlockBodyAltair::getAttestations,
                  BeaconBlockBodyAltair::setAttestations)
              .withField(
                  "deposits",
                  new DeserializableListTypeDefinition<>(DEPOSIT_TYPE),
                  BeaconBlockBodyAltair::getDeposits,
                  BeaconBlockBody::setDeposits)
              .withField(
                  "voluntary_exits",
                  new DeserializableListTypeDefinition<>(SIGNED_VOLUNTARY_EXIT_TYPE),
                  BeaconBlockBodyAltair::getVoluntaryExits,
                  BeaconBlockBodyAltair::setVoluntaryExits)
              .withField(
                  "sync_aggregate",
                  SYNC_AGGREGATE_TYPE,
                  BeaconBlockBodyAltair::getSyncAggregate,
                  BeaconBlockBodyAltair::setSyncAggregate)
              .build();

  public BeaconBlockBodyAltair() {}

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
    this.syncAggregate = new SyncAggregate(message.getSyncAggregate());
    checkNotNull(syncAggregate, "Sync Aggregate is required for altair blocks");
  }

  public SyncAggregate getSyncAggregate() {
    return syncAggregate;
  }

  public void setSyncAggregate(SyncAggregate syncAggregate) {
    this.syncAggregate = syncAggregate;
  }

  @Override
  public BeaconBlockBodySchemaAltair<?> getBeaconBlockBodySchema(final SpecVersion spec) {
    return (BeaconBlockBodySchemaAltair<?>) spec.getSchemaDefinitions().getBeaconBlockBodySchema();
  }

  @Override
  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody
      asInternalBeaconBlockBody(
          final SpecVersion spec, Consumer<BeaconBlockBodyBuilder> builderRef) {
    final SyncAggregateSchema syncAggregateSchema =
        getBeaconBlockBodySchema(spec).getSyncAggregateSchema();
    return super.asInternalBeaconBlockBody(
        spec,
        (builder) -> {
          builderRef.accept(builder);
          builder.syncAggregate(
              syncAggregateSchema.create(
                  syncAggregateSchema
                      .getSyncCommitteeBitsSchema()
                      .fromBytes(syncAggregate.syncCommitteeBits)
                      .getAllSetBits(),
                  syncAggregate.syncCommitteeSignature.asInternalBLSSignature()));
        });
  }
}

/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSSignatureDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSSignatureSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodySchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;

public class BlindedBeaconBlockBodyV1 {
  @JsonSerialize(using = BLSSignatureSerializer.class)
  @JsonDeserialize(using = BLSSignatureDeserializer.class)
  public final BLSSignature randaoReveal;

  public final Eth1DataV1 eth1Data;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 graffiti;

  public final List<ProposerSlashingV1> proposerSlashings;
  public final List<AttesterSlashingV1> attesterSlashings;
  public final List<AttestationV1> attestations;
  public final List<DepositV1> deposits;
  public final List<SignedMessage<VoluntaryExitV1>> voluntaryExits;
  public final SyncAggregateV1 syncAggregate;
  public final ExecutionPayloadHeaderV1 executionPayloadHeader;

  @JsonCreator
  public BlindedBeaconBlockBodyV1(
      @JsonProperty("randaoReveal") final BLSSignature randaoReveal,
      @JsonProperty("eth1Data") final Eth1DataV1 eth1Data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposerSlashings") final List<ProposerSlashingV1> proposerSlashings,
      @JsonProperty("attesterSlashings") final List<AttesterSlashingV1> attesterSlashings,
      @JsonProperty("attestations") final List<AttestationV1> attestations,
      @JsonProperty("deposits") final List<DepositV1> deposits,
      @JsonProperty("voluntaryExits") final List<SignedMessage<VoluntaryExitV1>> voluntaryExits,
      @JsonProperty("syncAggregate") final SyncAggregateV1 syncAggregate,
      @JsonProperty("executionPayloadHeader")
          final ExecutionPayloadHeaderV1 executionPayloadHeader) {
    this.randaoReveal = randaoReveal;
    this.eth1Data = eth1Data;
    this.graffiti = graffiti;
    this.proposerSlashings = proposerSlashings;
    this.attesterSlashings = attesterSlashings;
    this.attestations = attestations;
    this.deposits = deposits;
    this.voluntaryExits = voluntaryExits;
    this.syncAggregate = syncAggregate;
    this.executionPayloadHeader = executionPayloadHeader;
  }

  public BlindedBeaconBlockBodySchemaBellatrix<?> getBeaconBlockBodySchema(final SpecVersion spec) {
    return (BlindedBeaconBlockBodySchemaBellatrix<?>)
        spec.getSchemaDefinitions().getBlindedBeaconBlockBodySchema();
  }

  public BlindedBeaconBlockBodyV1(
      tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    this.randaoReveal = new BLSSignature(body.getRandaoReveal().toSSZBytes());
    this.eth1Data = new Eth1DataV1(body.getEth1Data());
    this.graffiti = body.getGraffiti();
    this.proposerSlashings =
        body.getProposerSlashings().stream()
            .map(ProposerSlashingV1::new)
            .collect(Collectors.toList());
    this.attesterSlashings =
        body.getAttesterSlashings().stream()
            .map(AttesterSlashingV1::new)
            .collect(Collectors.toList());
    this.attestations =
        body.getAttestations().stream().map(AttestationV1::new).collect(Collectors.toList());
    this.deposits = body.getDeposits().stream().map(DepositV1::new).collect(Collectors.toList());
    this.voluntaryExits =
        body.getVoluntaryExits().stream()
            .map(
                signedVoluntaryExit ->
                    new SignedMessage<>(
                        new VoluntaryExitV1(signedVoluntaryExit.getMessage()),
                        signedVoluntaryExit.getSignature()))
            .collect(Collectors.toList());
    this.syncAggregate = new SyncAggregateV1(body.getOptionalSyncAggregate().orElseThrow());
    this.executionPayloadHeader =
        ExecutionPayloadHeaderV1.fromInternalExecutionPayloadHeader(
            body.getOptionalExecutionPayloadHeader().orElseThrow());
  }

  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody
      asInternalBeaconBlockBody(final SpecVersion spec) {

    final ExecutionPayloadHeaderSchema executionPayloadHeaderSchema =
        getBeaconBlockBodySchema(spec).getExecutionPayloadHeaderSchema();
    final SyncAggregateSchema syncAggregateSchema =
        getBeaconBlockBodySchema(spec).getSyncAggregateSchema();
    final BeaconBlockBodySchema<?> schema = getBeaconBlockBodySchema(spec);

    return schema.createBlockBody(
        builder ->
            builder
                .randaoReveal(randaoReveal.asInternalBLSSignature())
                .eth1Data(
                    new tech.pegasys.teku.spec.datastructures.blocks.Eth1Data(
                        eth1Data.depositRoot, eth1Data.depositCount, eth1Data.blockHash))
                .graffiti(graffiti)
                .attestations(
                    attestations.stream()
                        .map(attestation -> attestation.asInternalAttestation(spec))
                        .collect(schema.getAttestationsSchema().collector()))
                .proposerSlashings(
                    proposerSlashings.stream()
                        .map(ProposerSlashingV1::asInternalProposerSlashing)
                        .collect(schema.getProposerSlashingsSchema().collector()))
                .attesterSlashings(
                    attesterSlashings.stream()
                        .map(slashing -> slashing.asInternalAttesterSlashing(spec))
                        .collect(schema.getAttesterSlashingsSchema().collector()))
                .deposits(
                    deposits.stream()
                        .map(DepositV1::asInternalDeposit)
                        .collect(schema.getDepositsSchema().collector()))
                .voluntaryExits(
                    voluntaryExits.stream()
                        .map(
                            voluntaryExit ->
                                new SignedVoluntaryExit(
                                    voluntaryExit.getMessage().asInternalVoluntaryExit(),
                                    voluntaryExit.getSignature().asInternalBLSSignature()))
                        .collect(schema.getVoluntaryExitsSchema().collector()))
                .syncAggregate(
                    () ->
                        syncAggregateSchema.create(
                            syncAggregateSchema
                                .getSyncCommitteeBitsSchema()
                                .fromBytes(syncAggregate.syncCommitteeBits)
                                .getAllSetBits(),
                            syncAggregate.syncCommitteeSignature.asInternalBLSSignature()))
                .executionPayloadHeader(
                    () ->
                        executionPayloadHeaderSchema.create(
                            executionPayloadHeader.parentHash,
                            executionPayloadHeader.feeRecipient,
                            executionPayloadHeader.stateRoot,
                            executionPayloadHeader.receiptsRoot,
                            executionPayloadHeader.logsBloom,
                            executionPayloadHeader.prevRandao,
                            executionPayloadHeader.blockNumber,
                            executionPayloadHeader.gasLimit,
                            executionPayloadHeader.gasUsed,
                            executionPayloadHeader.timestamp,
                            executionPayloadHeader.extraData,
                            executionPayloadHeader.baseFeePerGas,
                            executionPayloadHeader.blockHash,
                            executionPayloadHeader.transactionsRoot)));
  }
}

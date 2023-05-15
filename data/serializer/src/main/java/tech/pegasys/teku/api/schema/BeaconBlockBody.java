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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.api.schema.Attestation.ATTESTATION_TYPE;
import static tech.pegasys.teku.api.schema.AttesterSlashing.ATTESTER_SLASHING_TYPE;
import static tech.pegasys.teku.api.schema.BLSSignature.BLS_SIGNATURE_TYPE;
import static tech.pegasys.teku.api.schema.Deposit.DEPOSIT_TYPE;
import static tech.pegasys.teku.api.schema.Eth1Data.ETH_1_DATA_TYPE;
import static tech.pegasys.teku.api.schema.ProposerSlashing.PROPOSER_SLASHING_TYPE;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;
import static tech.pegasys.teku.api.schema.SignedVoluntaryExit.SIGNED_VOLUNTARY_EXIT_TYPE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableListTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;

@SuppressWarnings("JavaCase")
public class BeaconBlockBody {
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public BLSSignature randao_reveal;

  public Eth1Data eth1_data;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public Bytes32 graffiti;

  public List<ProposerSlashing> proposer_slashings;
  public List<AttesterSlashing> attester_slashings;
  public List<Attestation> attestations;
  public List<Deposit> deposits;
  public List<SignedVoluntaryExit> voluntary_exits;

  public static final DeserializableTypeDefinition<BeaconBlockBody> BEACON_BLOCK_BODY_TYPE =
      DeserializableTypeDefinition.object(BeaconBlockBody.class)
          .initializer(BeaconBlockBody::new)
          .withField(
              "randao_reveal",
              BLS_SIGNATURE_TYPE,
              BeaconBlockBody::getRandaoReveal,
              BeaconBlockBody::setRandaoReveal)
          .withField(
              "eth1_data",
              ETH_1_DATA_TYPE,
              BeaconBlockBody::getEth1Data,
              BeaconBlockBody::setEth1Data)
          .withField(
              "graffiti",
              CoreTypes.BYTES32_TYPE,
              BeaconBlockBody::getGraffiti,
              BeaconBlockBody::setGraffiti)
          .withField(
              "proposer_slashings",
              new DeserializableListTypeDefinition<>(PROPOSER_SLASHING_TYPE),
              BeaconBlockBody::getProposerSlashings,
              BeaconBlockBody::setProposerSlashings)
          .withField(
              "attester_slashings",
              new DeserializableListTypeDefinition<>(ATTESTER_SLASHING_TYPE),
              BeaconBlockBody::getAttesterSlashings,
              BeaconBlockBody::setAttesterSlashings)
          .withField(
              "attestations",
              new DeserializableListTypeDefinition<>(ATTESTATION_TYPE),
              BeaconBlockBody::getAttestations,
              BeaconBlockBody::setAttestations)
          .withField(
              "deposits",
              new DeserializableListTypeDefinition<>(DEPOSIT_TYPE),
              BeaconBlockBody::getDeposits,
              BeaconBlockBody::setDeposits)
          .withField(
              "voluntary_exits",
              new DeserializableListTypeDefinition<>(SIGNED_VOLUNTARY_EXIT_TYPE),
              BeaconBlockBody::getVoluntaryExits,
              BeaconBlockBody::setVoluntaryExits)
          .build();

  public BeaconBlockBody() {}

  @JsonCreator
  public BeaconBlockBody(
      @JsonProperty("randao_reveal") final BLSSignature randao_reveal,
      @JsonProperty("eth1_data") final Eth1Data eth1_data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposer_slashings") final List<ProposerSlashing> proposer_slashings,
      @JsonProperty("attester_slashings") final List<AttesterSlashing> attester_slashings,
      @JsonProperty("attestations") final List<Attestation> attestations,
      @JsonProperty("deposits") final List<Deposit> deposits,
      @JsonProperty("voluntary_exits") final List<SignedVoluntaryExit> voluntary_exits) {
    this.randao_reveal = randao_reveal;
    this.eth1_data = eth1_data;
    this.graffiti = graffiti;
    this.proposer_slashings = proposer_slashings;
    this.attester_slashings = attester_slashings;
    this.attestations = attestations;
    this.deposits = deposits;
    this.voluntary_exits = voluntary_exits;
  }

  public BeaconBlockBody(
      tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    this.randao_reveal = new BLSSignature(body.getRandaoReveal().toSSZBytes());
    this.eth1_data = new Eth1Data(body.getEth1Data());
    this.graffiti = body.getGraffiti();
    this.proposer_slashings =
        body.getProposerSlashings().stream()
            .map(ProposerSlashing::new)
            .collect(Collectors.toList());
    this.attester_slashings =
        body.getAttesterSlashings().stream()
            .map(AttesterSlashing::new)
            .collect(Collectors.toList());
    this.attestations =
        body.getAttestations().stream().map(Attestation::new).collect(Collectors.toList());
    this.deposits = body.getDeposits().stream().map(Deposit::new).collect(Collectors.toList());
    this.voluntary_exits =
        body.getVoluntaryExits().stream()
            .map(SignedVoluntaryExit::new)
            .collect(Collectors.toList());
  }

  public BLSSignature getRandaoReveal() {
    return randao_reveal;
  }

  public Eth1Data getEth1Data() {
    return eth1_data;
  }

  public Bytes32 getGraffiti() {
    return graffiti;
  }

  public List<ProposerSlashing> getProposerSlashings() {
    return proposer_slashings;
  }

  public List<AttesterSlashing> getAttesterSlashings() {
    return attester_slashings;
  }

  public List<Attestation> getAttestations() {
    return attestations;
  }

  public List<Deposit> getDeposits() {
    return deposits;
  }

  public List<SignedVoluntaryExit> getVoluntaryExits() {
    return voluntary_exits;
  }

  public void setRandaoReveal(BLSSignature randao_reveal) {
    this.randao_reveal = randao_reveal;
  }

  public void setEth1Data(Eth1Data eth1_data) {
    this.eth1_data = eth1_data;
  }

  public void setGraffiti(Bytes32 graffiti) {
    this.graffiti = graffiti;
  }

  public void setProposerSlashings(List<ProposerSlashing> proposer_slashings) {
    this.proposer_slashings = proposer_slashings;
  }

  public void setAttesterSlashings(List<AttesterSlashing> attester_slashings) {
    this.attester_slashings = attester_slashings;
  }

  public void setAttestations(List<Attestation> attestations) {
    this.attestations = attestations;
  }

  public void setDeposits(List<Deposit> deposits) {
    this.deposits = deposits;
  }

  public void setVoluntaryExits(List<SignedVoluntaryExit> voluntary_exits) {
    this.voluntary_exits = voluntary_exits;
  }

  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody
      asInternalBeaconBlockBody(
          final SpecVersion spec, Consumer<BeaconBlockBodyBuilder> builderRef) {
    final BeaconBlockBodySchema<?> schema = getBeaconBlockBodySchema(spec);
    return schema
        .createBlockBody(
            builder -> {
              builderRef.accept(builder);
              builder
                  .randaoReveal(randao_reveal.asInternalBLSSignature())
                  .eth1Data(
                      new tech.pegasys.teku.spec.datastructures.blocks.Eth1Data(
                          eth1_data.deposit_root, eth1_data.deposit_count, eth1_data.block_hash))
                  .graffiti(graffiti)
                  .attestations(
                      attestations.stream()
                          .map(attestation -> attestation.asInternalAttestation(spec))
                          .collect(schema.getAttestationsSchema().collector()))
                  .proposerSlashings(
                      proposer_slashings.stream()
                          .map(ProposerSlashing::asInternalProposerSlashing)
                          .collect(schema.getProposerSlashingsSchema().collector()))
                  .attesterSlashings(
                      attester_slashings.stream()
                          .map(slashing -> slashing.asInternalAttesterSlashing(spec))
                          .collect(schema.getAttesterSlashingsSchema().collector()))
                  .deposits(
                      deposits.stream()
                          .map(Deposit::asInternalDeposit)
                          .collect(schema.getDepositsSchema().collector()))
                  .voluntaryExits(
                      voluntary_exits.stream()
                          .map(SignedVoluntaryExit::asInternalSignedVoluntaryExit)
                          .collect(schema.getVoluntaryExitsSchema().collector()));
            })
        .join();
  }

  @JsonIgnore
  public boolean isBlinded() {
    return false;
  }

  public BeaconBlockBodySchema<?> getBeaconBlockBodySchema(final SpecVersion spec) {
    return spec.getSchemaDefinitions().getBeaconBlockBodySchema();
  }

  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody
      asInternalBeaconBlockBody(final SpecVersion spec) {
    return asInternalBeaconBlockBody(spec, (builder) -> {});
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BeaconBlockBody)) {
      return false;
    }
    BeaconBlockBody that = (BeaconBlockBody) o;
    return Objects.equals(randao_reveal, that.randao_reveal)
        && Objects.equals(eth1_data, that.eth1_data)
        && Objects.equals(graffiti, that.graffiti)
        && Objects.equals(proposer_slashings, that.proposer_slashings)
        && Objects.equals(attester_slashings, that.attester_slashings)
        && Objects.equals(attestations, that.attestations)
        && Objects.equals(deposits, that.deposits)
        && Objects.equals(voluntary_exits, that.voluntary_exits);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        randao_reveal,
        eth1_data,
        graffiti,
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits);
  }
}

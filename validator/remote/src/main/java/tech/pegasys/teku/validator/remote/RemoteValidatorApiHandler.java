/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.validator.remote;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.GetForkResponse;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.ValidatorDutiesRequest;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorDuties;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpValidatorRestApiClient;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorRestApiClient;

public class RemoteValidatorApiHandler implements ValidatorApiChannel {

  private final ValidatorRestApiClient apiClient;

  @SuppressWarnings("unused")
  public RemoteValidatorApiHandler(final ServiceConfig config) {
    // TODO: create config for validator client [https://github.com/PegaSysEng/teku/issues/2450]
    apiClient = new OkHttpValidatorRestApiClient("http://127.0.0.1:8888");
  }

  public RemoteValidatorApiHandler(final ValidatorRestApiClient apiClient) {
    this.apiClient = apiClient;
  }

  @Override
  public SafeFuture<Optional<ForkInfo>> getForkInfo() {
    return SafeFuture.supplyAsync(() -> apiClient.getFork().map(this::mapGetForkResponse));
  }

  private ForkInfo mapGetForkResponse(final GetForkResponse response) {
    final Fork fork = new Fork(response.previous_version, response.current_version, response.epoch);
    return new ForkInfo(fork, response.genesis_validators_root);
  }

  @Override
  public SafeFuture<Optional<List<ValidatorDuties>>> getDuties(
      final UInt64 epoch, final Collection<BLSPublicKey> publicKeys) {
    if (publicKeys.isEmpty()) {
      return SafeFuture.completedFuture(Optional.of(Collections.emptyList()));
    }

    return SafeFuture.supplyAsync(
        () -> {
          final List<BLSPubKey> blsPubKeys =
              publicKeys.stream().map(BLSPubKey::new).collect(Collectors.toList());
          final ValidatorDutiesRequest validatorDutiesRequest =
              new ValidatorDutiesRequest(epoch, blsPubKeys);

          final List<ValidatorDuties> validatorDuties =
              apiClient.getDuties(validatorDutiesRequest).stream()
                  .map(this::mapToApiValidatorDuties)
                  .collect(Collectors.toList());

          return Optional.of(validatorDuties);
        });
  }

  private ValidatorDuties mapToApiValidatorDuties(
      final tech.pegasys.teku.api.schema.ValidatorDuties schemaValidatorDuties) {
    return ValidatorDuties.withDuties(
        schemaValidatorDuties.validator_pubkey.asBLSPublicKey(),
        schemaValidatorDuties.validator_index,
        schemaValidatorDuties.attestation_committee_index,
        schemaValidatorDuties.attestation_committee_position,
        schemaValidatorDuties.aggregator_modulo,
        schemaValidatorDuties.block_proposal_slots,
        schemaValidatorDuties.attestation_slot);
  }

  @Override
  public SafeFuture<Optional<Attestation>> createUnsignedAttestation(
      final UInt64 slot, final int committeeIndex) {
    return SafeFuture.supplyAsync(
        () ->
            apiClient
                .createUnsignedAttestation(slot, committeeIndex)
                .map(tech.pegasys.teku.api.schema.Attestation::asInternalAttestation));
  }

  @Override
  public void sendSignedAttestation(final Attestation attestation) {
    final tech.pegasys.teku.api.schema.Attestation schemaAttestation =
        new tech.pegasys.teku.api.schema.Attestation(attestation);

    apiClient.sendSignedAttestation(schemaAttestation);
  }

  @Override
  public void sendSignedAttestation(
      final Attestation attestation, final Optional<Integer> validatorIndex) {
    sendSignedAttestation(attestation);
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UInt64 slot, final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    return SafeFuture.supplyAsync(
        () -> {
          final tech.pegasys.teku.api.schema.BLSSignature schemaBLSSignature =
              new tech.pegasys.teku.api.schema.BLSSignature(randaoReveal);

          return apiClient
              .createUnsignedBlock(slot, schemaBLSSignature, graffiti)
              .map(tech.pegasys.teku.api.schema.BeaconBlock::asInternalBeaconBlock);
        });
  }

  @Override
  public void sendSignedBlock(final SignedBeaconBlock block) {
    apiClient.sendSignedBlock(new tech.pegasys.teku.api.schema.SignedBeaconBlock(block));
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(final Bytes32 attestationHashTreeRoot) {
    return SafeFuture.supplyAsync(
        () ->
            apiClient
                .createAggregate(attestationHashTreeRoot)
                .map(tech.pegasys.teku.api.schema.Attestation::asInternalAttestation));
  }

  @Override
  public void sendAggregateAndProof(final SignedAggregateAndProof aggregateAndProof) {
    apiClient.sendAggregateAndProof(
        new tech.pegasys.teku.api.schema.SignedAggregateAndProof(aggregateAndProof));
  }

  @Override
  public void subscribeToBeaconCommitteeForAggregation(
      final int committeeIndex, final UInt64 aggregationSlot) {
    apiClient.subscribeToBeaconCommitteeForAggregation(committeeIndex, aggregationSlot);
  }

  @Override
  public void subscribeToPersistentSubnets(final Set<SubnetSubscription> subnetSubscriptions) {
    final Set<tech.pegasys.teku.api.schema.SubnetSubscription> schemaSubscriptions =
        subnetSubscriptions.stream()
            .map(
                s ->
                    new tech.pegasys.teku.api.schema.SubnetSubscription(
                        s.getSubnetId(), s.getUnsubscriptionSlot()))
            .collect(Collectors.toSet());

    apiClient.subscribeToPersistentSubnets(schemaSubscriptions);
  }
}

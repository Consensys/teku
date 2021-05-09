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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;

import com.google.common.base.Throwables;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.v1.beacon.PostSyncCommitteeFailureResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.response.v1.validator.PostSyncDutiesResponse;
import tech.pegasys.teku.api.schema.altair.ContributionAndProof;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingRunnable;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.ProposerDuty;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitCommitteeSignatureError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeDuty;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.remote.apiclient.RateLimitedException;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorRestApiClient;

public class RemoteValidatorApiHandler implements ValidatorApiChannel {

  private static final Logger LOG = LogManager.getLogger();
  static final int MAX_PUBLIC_KEY_BATCH_SIZE = 10;
  static final int MAX_RATE_LIMITING_RETRIES = 3;

  private final Spec spec;
  private final ValidatorRestApiClient apiClient;
  private final AsyncRunner asyncRunner;

  public RemoteValidatorApiHandler(
      final Spec spec, final ValidatorRestApiClient apiClient, final AsyncRunner asyncRunner) {
    this.spec = spec;
    this.apiClient = apiClient;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return sendRequest(
        () ->
            apiClient
                .getGenesis()
                .map(
                    response ->
                        new GenesisData(
                            response.data.genesisTime, response.data.genesisValidatorsRoot)));
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys) {
    if (publicKeys.isEmpty()) {
      return SafeFuture.completedFuture(emptyMap());
    }
    return sendRequest(
        () ->
            makeBatchedValidatorRequest(publicKeys, ValidatorResponse::getIndex)
                .orElse(emptyMap()));
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      Collection<BLSPublicKey> publicKeys) {
    return sendRequest(() -> makeBatchedValidatorRequest(publicKeys, ValidatorResponse::getStatus));
  }

  private <T> Optional<Map<BLSPublicKey, T>> makeBatchedValidatorRequest(
      Collection<BLSPublicKey> publicKeysCollection,
      Function<ValidatorResponse, T> valueExtractor) {
    final List<BLSPublicKey> publicKeys = new ArrayList<>(publicKeysCollection);
    final Map<BLSPublicKey, T> returnedObjects = new HashMap<>();
    for (int i = 0; i < publicKeys.size(); i += MAX_PUBLIC_KEY_BATCH_SIZE) {
      final List<BLSPublicKey> batch =
          publicKeys.subList(i, Math.min(publicKeys.size(), i + MAX_PUBLIC_KEY_BATCH_SIZE));
      Optional<Map<BLSPublicKey, T>> validatorObjects =
          requestValidatorObject(batch, valueExtractor);
      if (validatorObjects.isEmpty()) {
        return Optional.empty();
      }
      returnedObjects.putAll(validatorObjects.get());
    }
    return Optional.of(returnedObjects);
  }

  private <T> Optional<Map<BLSPublicKey, T>> requestValidatorObject(
      final List<BLSPublicKey> batch, Function<ValidatorResponse, T> valueExtractor) {
    return apiClient
        .getValidators(
            batch.stream()
                .map(key -> key.toBytesCompressed().toHexString())
                .collect(Collectors.toList()))
        .map(responses -> convertToValidatorMap(responses, valueExtractor));
  }

  private <T> Map<BLSPublicKey, T> convertToValidatorMap(
      final List<ValidatorResponse> validatorResponses,
      Function<ValidatorResponse, T> valueExtractor) {
    return validatorResponses.stream()
        .collect(toMap(ValidatorResponse::getPublicKey, valueExtractor));
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndexes) {
    return sendRequest(
        () ->
            apiClient
                .getAttestationDuties(epoch, validatorIndexes)
                .map(
                    response ->
                        new AttesterDuties(
                            response.dependentRoot,
                            response.data.stream()
                                .map(this::mapToApiAttesterDuties)
                                .collect(Collectors.toList()))));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    return sendRequest(
        () ->
            apiClient
                .getSyncCommitteeDuties(epoch, validatorIndices)
                .map(this::responseToSyncCommitteeDuties));
  }

  private SyncCommitteeDuties responseToSyncCommitteeDuties(final PostSyncDutiesResponse response) {
    return new SyncCommitteeDuties(
        response.data.stream()
            .map(
                duty ->
                    new SyncCommitteeDuty(
                        duty.pubkey.asBLSPublicKey(),
                        duty.validatorIndex.intValue(),
                        duty.committeeIndices.stream()
                            .map(UInt64::intValue)
                            .collect(Collectors.toSet())))
            .collect(Collectors.toList()));
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(final UInt64 epoch) {
    return sendRequest(
        () ->
            apiClient
                .getProposerDuties(epoch)
                .map(
                    response ->
                        new ProposerDuties(
                            response.dependentRoot,
                            response.data.stream()
                                .map(this::mapToProposerDuties)
                                .collect(Collectors.toList()))));
  }

  private ProposerDuty mapToProposerDuties(
      final tech.pegasys.teku.api.response.v1.validator.ProposerDuty proposerDuty) {
    return new ProposerDuty(
        proposerDuty.pubkey.asBLSPublicKey(),
        proposerDuty.validatorIndex.intValue(),
        proposerDuty.slot);
  }

  private AttesterDuty mapToApiAttesterDuties(
      final tech.pegasys.teku.api.response.v1.validator.AttesterDuty attesterDuty) {
    return new AttesterDuty(
        attesterDuty.pubkey.asBLSPublicKey(),
        attesterDuty.validatorIndex.intValue(),
        attesterDuty.committeeLength.intValue(),
        attesterDuty.committeeIndex.intValue(),
        attesterDuty.committeesAtSlot.intValue(),
        attesterDuty.validatorCommitteeIndex.intValue(),
        attesterDuty.slot);
  }

  @Override
  public SafeFuture<Optional<Attestation>> createUnsignedAttestation(
      final UInt64 slot, final int committeeIndex) {
    return sendRequest(
        () ->
            apiClient
                .createUnsignedAttestation(slot, committeeIndex)
                .map(tech.pegasys.teku.api.schema.Attestation::asInternalAttestation));
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return sendRequest(
        () ->
            apiClient
                .createAttestationData(slot, committeeIndex)
                .map(tech.pegasys.teku.api.schema.AttestationData::asInternalAttestationData));
  }

  @Override
  public void sendSignedAttestation(final Attestation attestation) {
    final tech.pegasys.teku.api.schema.Attestation schemaAttestation =
        new tech.pegasys.teku.api.schema.Attestation(attestation);

    sendRequest(() -> apiClient.sendSignedAttestation(schemaAttestation))
        .finish(error -> LOG.error("Failed to send signed attestation", error));
  }

  @Override
  public void sendSignedAttestation(
      final Attestation attestation, final Optional<Integer> validatorIndex) {
    sendSignedAttestation(attestation);
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UInt64 slot, final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    return sendRequest(
        () -> {
          final tech.pegasys.teku.api.schema.BLSSignature schemaBLSSignature =
              new tech.pegasys.teku.api.schema.BLSSignature(randaoReveal);

          return apiClient
              .createUnsignedBlock(slot, schemaBLSSignature, graffiti)
              .map(block -> block.asInternalBeaconBlock(spec));
        });
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(final SignedBeaconBlock block) {
    return sendRequest(
        () -> apiClient.sendSignedBlock(new tech.pegasys.teku.api.schema.SignedBeaconBlock(block)));
  }

  @Override
  public SafeFuture<List<SubmitCommitteeSignatureError>> sendSyncCommitteeSignatures(
      final List<SyncCommitteeSignature> syncCommitteeSignatures) {
    return sendRequest(
        () ->
            apiClient
                .sendSyncCommitteeSignatures(
                    syncCommitteeSignatures.stream()
                        .map(
                            signature ->
                                new tech.pegasys.teku.api.schema.altair.SyncCommitteeSignature(
                                    signature.getSlot(),
                                    signature.getBeaconBlockRoot(),
                                    signature.getValidatorIndex(),
                                    new tech.pegasys.teku.api.schema.BLSSignature(
                                        signature.getSignature())))
                        .collect(Collectors.toList()))
                .map(this::responseToSyncCommitteeSignatures)
                .orElse(emptyList()));
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    final List<tech.pegasys.teku.api.schema.altair.SignedContributionAndProof>
        signedContributionsRestSchema =
            signedContributionAndProofs.stream()
                .map(this::asSignedContributionandProofs)
                .collect(Collectors.toList());
    return sendRequest(() -> apiClient.sendContributionAndProofs(signedContributionsRestSchema));
  }

  private tech.pegasys.teku.api.schema.altair.SignedContributionAndProof
      asSignedContributionandProofs(final SignedContributionAndProof signedContributionAndProof) {
    return new tech.pegasys.teku.api.schema.altair.SignedContributionAndProof(
        asContributionAndProof(signedContributionAndProof.getMessage()),
        new tech.pegasys.teku.api.schema.BLSSignature(signedContributionAndProof.getSignature()));
  }

  private ContributionAndProof asContributionAndProof(
      final tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof
          message) {
    return new ContributionAndProof(
        message.getAggregatorIndex(),
        new tech.pegasys.teku.api.schema.BLSSignature(message.getSelectionProof()),
        asSyncCommitteeContribution(message.getContribution()));
  }

  private tech.pegasys.teku.api.schema.altair.SyncCommitteeContribution asSyncCommitteeContribution(
      final SyncCommitteeContribution contribution) {
    return new tech.pegasys.teku.api.schema.altair.SyncCommitteeContribution(
        contribution.getSlot(),
        contribution.getBeaconBlockRoot(),
        contribution.getSubcommitteeIndex(),
        contribution.getAggregationBits(),
        new tech.pegasys.teku.api.schema.BLSSignature(contribution.getSignature()));
  }

  private List<SubmitCommitteeSignatureError> responseToSyncCommitteeSignatures(
      final PostSyncCommitteeFailureResponse postSyncCommitteeFailureResponse) {
    return postSyncCommitteeFailureResponse.failures.stream()
        .map(i -> new SubmitCommitteeSignatureError(i.index, i.message))
        .collect(Collectors.toList());
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    return sendRequest(
        () ->
            apiClient
                .createAggregate(slot, attestationHashTreeRoot)
                .map(tech.pegasys.teku.api.schema.Attestation::asInternalAttestation));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    throw new UnsupportedOperationException(
        "REST API for createSyncCommitteeContribution not supported");
  }

  @Override
  public void sendAggregateAndProof(final SignedAggregateAndProof aggregateAndProof) {
    sendRequest(
            () ->
                apiClient.sendAggregateAndProofs(
                    List.of(
                        new tech.pegasys.teku.api.schema.SignedAggregateAndProof(
                            aggregateAndProof))))
        .finish(error -> LOG.error("Failed to send aggregate and proof", error));
  }

  @Override
  public void subscribeToBeaconCommittee(final List<CommitteeSubscriptionRequest> requests) {
    sendRequest(() -> apiClient.subscribeToBeaconCommittee(requests))
        .finish(
            error -> LOG.error("Failed to subscribe to beacon committee for aggregation", error));
  }

  @Override
  public void subscribeToSyncCommitteeSubnets(
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    sendRequest(
            () ->
                apiClient.subscribeToSyncCommitteeSubnets(
                    subscriptions.stream()
                        .map(
                            subscription ->
                                new tech.pegasys.teku.api.schema.altair
                                    .SyncCommitteeSubnetSubscription(
                                    UInt64.valueOf(subscription.getValidatorIndex()),
                                    subscription.getSyncCommitteeIndices().stream()
                                        .map(UInt64::valueOf)
                                        .collect(Collectors.toList()),
                                    subscription.getUntilEpoch()))
                        .collect(Collectors.toList())))
        .finish(error -> LOG.error("Failed to subscribe to sync committee subnets", error));
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

    sendRequest(() -> apiClient.subscribeToPersistentSubnets(schemaSubscriptions))
        .finish(error -> LOG.error("Failed to subscribe to persistent subnets", error));
  }

  private SafeFuture<Void> sendRequest(final ExceptionThrowingRunnable requestExecutor) {
    return sendRequest(
        () -> {
          requestExecutor.run();
          return null;
        });
  }

  private <T> SafeFuture<T> sendRequest(final ExceptionThrowingSupplier<T> requestExecutor) {
    return asyncRunner.runAsync(() -> sendRequest(requestExecutor, 0));
  }

  private <T> SafeFuture<T> sendRequest(
      final ExceptionThrowingSupplier<T> requestExecutor, final int attempt) {
    return SafeFuture.of(requestExecutor)
        .exceptionallyCompose(
            error -> {
              if (Throwables.getRootCause(error) instanceof RateLimitedException
                  && attempt < MAX_RATE_LIMITING_RETRIES) {
                LOG.warn("Beacon node request rate limit has been exceeded. Retrying after delay.");
                return asyncRunner.runAfterDelay(
                    () -> sendRequest(requestExecutor, attempt + 1), Duration.ofSeconds(2));
              } else {
                return SafeFuture.failedFuture(error);
              }
            });
  }
}

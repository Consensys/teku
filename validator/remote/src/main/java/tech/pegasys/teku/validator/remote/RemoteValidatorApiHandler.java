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

package tech.pegasys.teku.validator.remote;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.google.common.base.Throwables;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
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
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.response.v1.validator.PostSyncDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.PostValidatorLivenessResponse;
import tech.pegasys.teku.api.schema.altair.ContributionAndProof;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingRunnable;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlindedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.ProposerDuty;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeDuty;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpValidatorRestApiClient;
import tech.pegasys.teku.validator.remote.apiclient.RateLimitedException;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorRestApiClient;
import tech.pegasys.teku.validator.remote.typedef.OkHttpValidatorTypeDefClient;

public class RemoteValidatorApiHandler implements RemoteValidatorApiChannel {

  private static final Logger LOG = LogManager.getLogger();
  static final int MAX_PUBLIC_KEY_BATCH_SIZE = 50;
  static final int MAX_RATE_LIMITING_RETRIES = 3;

  private final HttpUrl endpoint;
  private final Spec spec;
  private final ValidatorRestApiClient apiClient;
  private final OkHttpValidatorTypeDefClient typeDefClient;
  private final AsyncRunner asyncRunner;

  public RemoteValidatorApiHandler(
      final HttpUrl endpoint,
      final Spec spec,
      final ValidatorRestApiClient apiClient,
      final OkHttpValidatorTypeDefClient typeDefClient,
      final AsyncRunner asyncRunner) {
    this.endpoint = endpoint;
    this.spec = spec;
    this.apiClient = apiClient;
    this.asyncRunner = asyncRunner;
    this.typeDefClient = typeDefClient;
  }

  @Override
  public HttpUrl getEndpoint() {
    return endpoint;
  }

  @Override
  public SafeFuture<SyncingStatus> getSyncingStatus() {
    return sendRequest(typeDefClient::getSyncingStatus);
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return sendRequest(typeDefClient::getGenesis);
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
            batch.stream().map(key -> key.toBytesCompressed().toHexString()).collect(toList()))
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
      final UInt64 epoch, final IntCollection validatorIndices) {
    return sendRequest(
        () ->
            apiClient
                .getAttestationDuties(epoch, validatorIndices)
                .map(
                    response ->
                        new AttesterDuties(
                            response.executionOptimistic,
                            response.finalized,
                            response.dependentRoot,
                            response.data.stream()
                                .map(this::mapToApiAttesterDuties)
                                .collect(toList()))));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return sendRequest(
        () ->
            apiClient
                .getSyncCommitteeDuties(epoch, validatorIndices)
                .map(this::responseToSyncCommitteeDuties));
  }

  private SyncCommitteeDuties responseToSyncCommitteeDuties(final PostSyncDutiesResponse response) {
    return new SyncCommitteeDuties(
        response.executionOptimistic,
        response.data.stream()
            .map(
                duty ->
                    new SyncCommitteeDuty(
                        duty.pubkey.asBLSPublicKey(),
                        duty.validatorIndex.intValue(),
                        IntOpenHashSet.toSet(
                            duty.committeeIndices.stream().mapToInt(UInt64::intValue))))
            .collect(toList()));
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
                            response.data.stream().map(this::mapToProposerDuties).collect(toList()),
                            response.executionOptimistic)));
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
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return sendRequest(() -> typeDefClient.createAttestationData(slot, committeeIndex));
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(
      final List<Attestation> attestations) {
    final List<tech.pegasys.teku.api.schema.Attestation> schemaAttestations =
        attestations.stream().map(tech.pegasys.teku.api.schema.Attestation::new).collect(toList());

    return sendRequest(
        () ->
            apiClient
                .sendSignedAttestations(schemaAttestations)
                .map(this::convertPostDataFailureResponseToSubmitDataErrors)
                .orElse(emptyList()));
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean blinded) {
    return sendRequest(
        () -> typeDefClient.createUnsignedBlock(slot, randaoReveal, graffiti, blinded));
  }

  @Override
  public SafeFuture<Optional<BlindedBlockContents>> createUnsignedBlindedBlockContents(
      final UInt64 slot, final BLSSignature randaoReveal, Optional<Bytes32> graffiti) {
    return sendRequest(
        () -> typeDefClient.createUnsignedBlindedBlockContents(slot, randaoReveal, graffiti));
  }

  @Override
  public SafeFuture<Optional<BlockContents>> createUnsignedBlockContents(
      final UInt64 slot, final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    return sendRequest(
        () -> typeDefClient.createUnsignedBlockContents(slot, randaoReveal, graffiti));
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(final SignedBeaconBlock block) {
    return sendRequest(() -> typeDefClient.sendSignedBlock(block));
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return sendRequest(
        () ->
            apiClient
                .sendSyncCommitteeMessages(
                    syncCommitteeMessages.stream()
                        .map(
                            signature ->
                                new tech.pegasys.teku.api.schema.altair.SyncCommitteeMessage(
                                    signature.getSlot(),
                                    signature.getBeaconBlockRoot(),
                                    signature.getValidatorIndex(),
                                    new tech.pegasys.teku.api.schema.BLSSignature(
                                        signature.getSignature())))
                        .collect(toList()))
                .map(this::convertPostDataFailureResponseToSubmitDataErrors)
                .orElse(emptyList()));
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    final List<tech.pegasys.teku.api.schema.altair.SignedContributionAndProof>
        signedContributionsRestSchema =
            signedContributionAndProofs.stream()
                .map(this::asSignedContributionAndProofs)
                .collect(toList());
    return sendRequest(() -> apiClient.sendContributionAndProofs(signedContributionsRestSchema));
  }

  private tech.pegasys.teku.api.schema.altair.SignedContributionAndProof
      asSignedContributionAndProofs(final SignedContributionAndProof signedContributionAndProof) {
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
        contribution.getAggregationBits().sszSerialize(),
        new tech.pegasys.teku.api.schema.BLSSignature(contribution.getSignature()));
  }

  private List<SubmitDataError> convertPostDataFailureResponseToSubmitDataErrors(
      final PostDataFailureResponse postDataFailureResponse) {
    return postDataFailureResponse.failures.stream()
        .map(i -> new SubmitDataError(i.index, i.message))
        .collect(toList());
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    return sendRequest(
        () ->
            apiClient
                .createAggregate(slot, attestationHashTreeRoot)
                .map(attestation -> attestation.asInternalAttestation(spec)));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    return sendRequest(
        () ->
            apiClient
                .createSyncCommitteeContribution(slot, subcommitteeIndex, beaconBlockRoot)
                .map(
                    contribution ->
                        tech.pegasys.teku.api.schema.altair.SyncCommitteeContribution
                            .asInternalSyncCommitteeContribution(spec, contribution)));
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return sendRequest(
        () ->
            apiClient
                .sendAggregateAndProofs(
                    aggregateAndProofs.stream()
                        .map(tech.pegasys.teku.api.schema.SignedAggregateAndProof::new)
                        .collect(toList()))
                .map(this::convertPostDataFailureResponseToSubmitDataErrors)
                .orElse(emptyList()));
  }

  @Override
  public SafeFuture<Void> subscribeToBeaconCommittee(
      final List<CommitteeSubscriptionRequest> requests) {
    return sendRequest(() -> apiClient.subscribeToBeaconCommittee(requests));
  }

  @Override
  public SafeFuture<Void> subscribeToSyncCommitteeSubnets(
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    return sendRequest(
        () ->
            apiClient.subscribeToSyncCommitteeSubnets(
                subscriptions.stream()
                    .map(
                        subscription ->
                            new tech.pegasys.teku.api.schema.altair.SyncCommitteeSubnetSubscription(
                                UInt64.valueOf(subscription.getValidatorIndex()),
                                subscription
                                    .getSyncCommitteeIndices()
                                    .intStream()
                                    .mapToObj(UInt64::valueOf)
                                    .collect(toList()),
                                subscription.getUntilEpoch()))
                    .collect(toList())));
  }

  @Override
  public SafeFuture<Void> subscribeToPersistentSubnets(
      final Set<SubnetSubscription> subnetSubscriptions) {
    final Set<tech.pegasys.teku.api.schema.SubnetSubscription> schemaSubscriptions =
        subnetSubscriptions.stream()
            .map(
                s ->
                    new tech.pegasys.teku.api.schema.SubnetSubscription(
                        s.getSubnetId(), s.getUnsubscriptionSlot()))
            .collect(Collectors.toSet());

    return sendRequest(() -> apiClient.subscribeToPersistentSubnets(schemaSubscriptions));
  }

  @Override
  public SafeFuture<Void> prepareBeaconProposer(
      final Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    return sendRequest(
        () ->
            apiClient.prepareBeaconProposer(
                beaconPreparableProposers.stream()
                    .map(
                        proposer ->
                            new tech.pegasys.teku.api.schema.bellatrix.BeaconPreparableProposer(
                                proposer.getValidatorIndex(), proposer.getFeeRecipient()))
                    .collect(toList())));
  }

  @Override
  public SafeFuture<Void> registerValidators(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    return sendRequest(() -> typeDefClient.registerValidators(validatorRegistrations));
  }

  @Override
  public SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorsLiveness(
      List<UInt64> validatorIndices, UInt64 epoch) {
    return sendRequest(
        () ->
            apiClient
                .sendValidatorsLiveness(epoch, validatorIndices)
                .map(this::responseToDoppelgangerDetectionResult));
  }

  private List<ValidatorLivenessAtEpoch> responseToDoppelgangerDetectionResult(
      final PostValidatorLivenessResponse response) {
    return response.data.stream()
        .map(
            validatorLivenessAtEpoch ->
                new ValidatorLivenessAtEpoch(
                    validatorLivenessAtEpoch.index,
                    validatorLivenessAtEpoch.epoch,
                    validatorLivenessAtEpoch.isLive))
        .collect(Collectors.toList());
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

  public static RemoteValidatorApiHandler create(
      final HttpUrl endpoint,
      final OkHttpClient httpClient,
      final Spec spec,
      final boolean preferSszBlockEncoding,
      final AsyncRunner asyncRunner) {
    final OkHttpValidatorRestApiClient apiClient =
        new OkHttpValidatorRestApiClient(endpoint, httpClient);
    final OkHttpValidatorTypeDefClient typeDefClient =
        new OkHttpValidatorTypeDefClient(httpClient, endpoint, spec, preferSszBlockEncoding);
    return new RemoteValidatorApiHandler(endpoint, spec, apiClient, typeDefClient, asyncRunner);
  }
}

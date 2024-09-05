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

package tech.pegasys.teku.validator.remote;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;

import com.google.common.base.Throwables;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.ethereum.json.types.node.PeerCount;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingRunnable;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.remote.apiclient.PostStateValidatorsNotExistingException;
import tech.pegasys.teku.validator.remote.apiclient.RateLimitedException;
import tech.pegasys.teku.validator.remote.typedef.OkHttpValidatorTypeDefClient;

public class RemoteValidatorApiHandler implements RemoteValidatorApiChannel {

  private static final Logger LOG = LogManager.getLogger();

  static final int MAX_PUBLIC_KEY_BATCH_SIZE = 50;
  static final int MAX_RATE_LIMITING_RETRIES = 3;

  private final HttpUrl endpoint;
  private final OkHttpValidatorTypeDefClient typeDefClient;
  private final AsyncRunner asyncRunner;
  private final AtomicBoolean usePostValidatorsEndpoint;

  public RemoteValidatorApiHandler(
      final HttpUrl endpoint,
      final OkHttpValidatorTypeDefClient typeDefClient,
      final AsyncRunner asyncRunner,
      final boolean usePostValidatorsEndpoint) {
    this.endpoint = endpoint;
    this.asyncRunner = asyncRunner;
    this.typeDefClient = typeDefClient;
    this.usePostValidatorsEndpoint = new AtomicBoolean(usePostValidatorsEndpoint);
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
            makeValidatorRequest(publicKeys, StateValidatorData::getIntegerIndex)
                .orElse(emptyMap()));
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      final Collection<BLSPublicKey> publicKeys) {
    return sendRequest(() -> makeValidatorRequest(publicKeys, StateValidatorData::getStatus));
  }

  private <T> Optional<Map<BLSPublicKey, T>> makeValidatorRequest(
      final Collection<BLSPublicKey> publicKeys,
      final Function<StateValidatorData, T> valueExtractor) {
    if (usePostValidatorsEndpoint.get()) {
      try {
        return typeDefClient
            .postStateValidators(convertPublicKeysToValidatorIds(publicKeys))
            .map(responses -> convertToValidatorMap(responses, valueExtractor));
      } catch (final PostStateValidatorsNotExistingException __) {
        LOG.debug(
            "POST method is not available for getting validators from state. Will use GET instead.");
        usePostValidatorsEndpoint.set(false);
        return makeBatchedValidatorRequest(publicKeys, valueExtractor);
      }
    } else {
      return makeBatchedValidatorRequest(publicKeys, valueExtractor);
    }
  }

  private List<String> convertPublicKeysToValidatorIds(final Collection<BLSPublicKey> publicKeys) {
    return publicKeys.stream().map(BLSPublicKey::toHexString).toList();
  }

  private <T> Map<BLSPublicKey, T> convertToValidatorMap(
      final List<StateValidatorData> validatorData,
      final Function<StateValidatorData, T> valueExtractor) {
    return validatorData.stream().collect(toMap(StateValidatorData::getPublicKey, valueExtractor));
  }

  private <T> Optional<Map<BLSPublicKey, T>> makeBatchedValidatorRequest(
      final Collection<BLSPublicKey> publicKeysCollection,
      final Function<StateValidatorData, T> valueExtractor) {
    final List<BLSPublicKey> publicKeys = new ArrayList<>(publicKeysCollection);
    final Map<BLSPublicKey, T> returnedObjects = new HashMap<>();
    for (int i = 0; i < publicKeys.size(); i += MAX_PUBLIC_KEY_BATCH_SIZE) {
      final List<BLSPublicKey> batch =
          publicKeys.subList(i, Math.min(publicKeys.size(), i + MAX_PUBLIC_KEY_BATCH_SIZE));
      final Optional<Map<BLSPublicKey, T>> validatorObjects =
          requestValidatorObject(batch, valueExtractor);
      if (validatorObjects.isEmpty()) {
        return Optional.empty();
      }
      returnedObjects.putAll(validatorObjects.get());
    }
    return Optional.of(returnedObjects);
  }

  private <T> Optional<Map<BLSPublicKey, T>> requestValidatorObject(
      final List<BLSPublicKey> batch, final Function<StateValidatorData, T> valueExtractor) {
    return typeDefClient
        .getStateValidators(convertPublicKeysToValidatorIds(batch))
        .map(responses -> convertToValidatorMapTypeDef(responses, valueExtractor));
  }

  private <T> Map<BLSPublicKey, T> convertToValidatorMapTypeDef(
      final List<StateValidatorData> validatorData,
      final Function<StateValidatorData, T> valueExtractor) {
    return validatorData.stream().collect(toMap(StateValidatorData::getPublicKey, valueExtractor));
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return sendRequest(() -> typeDefClient.postAttesterDuties(epoch, validatorIndices));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return sendRequest(() -> typeDefClient.postSyncDuties(epoch, validatorIndices));
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(final UInt64 epoch) {
    return sendRequest(() -> typeDefClient.getProposerDuties(epoch));
  }

  @Override
  public SafeFuture<Optional<PeerCount>> getPeerCount() {
    return sendRequest(typeDefClient::getPeerCount);
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return sendRequest(() -> typeDefClient.createAttestationData(slot, committeeIndex));
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(
      final List<Attestation> attestations) {
    return sendRequest(() -> typeDefClient.sendSignedAttestations(attestations));
  }

  @Override
  public SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final Optional<UInt64> requestedBuilderBoostFactor) {
    return sendRequest(
        () ->
            typeDefClient.createUnsignedBlock(
                slot, randaoReveal, graffiti, requestedBuilderBoostFactor));
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel) {
    return sendRequest(
        () -> typeDefClient.sendSignedBlock(blockContainer, broadcastValidationLevel));
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return sendRequest(() -> typeDefClient.sendSyncCommitteeMessages(syncCommitteeMessages));
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    return sendRequest(() -> typeDefClient.sendContributionAndProofs(signedContributionAndProofs));
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot,
      final Bytes32 attestationHashTreeRoot,
      final Optional<UInt64> committeeIndex) {
    return sendRequest(
        () ->
            typeDefClient
                .createAggregate(slot, attestationHashTreeRoot, committeeIndex)
                .map(ObjectAndMetaData::getData));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    return sendRequest(
        () ->
            typeDefClient.createSyncCommitteeContribution(
                slot, subcommitteeIndex, beaconBlockRoot));
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return sendRequest(() -> typeDefClient.sendAggregateAndProofs(aggregateAndProofs));
  }

  @Override
  public SafeFuture<Void> subscribeToBeaconCommittee(
      final List<CommitteeSubscriptionRequest> requests) {
    return sendRequest(() -> typeDefClient.subscribeToBeaconCommittee(requests));
  }

  @Override
  public SafeFuture<Void> subscribeToSyncCommitteeSubnets(
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    return sendRequest(() -> typeDefClient.subscribeToSyncCommitteeSubnets(subscriptions));
  }

  @Override
  public SafeFuture<Void> subscribeToPersistentSubnets(
      final Set<SubnetSubscription> subnetSubscriptions) {
    return sendRequest(() -> typeDefClient.subscribeToPersistentSubnets(subnetSubscriptions));
  }

  @Override
  public SafeFuture<Void> prepareBeaconProposer(
      final Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    return sendRequest(
        () -> typeDefClient.prepareBeaconProposer(new ArrayList<>(beaconPreparableProposers)));
  }

  @Override
  public SafeFuture<Void> registerValidators(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    return sendRequest(() -> typeDefClient.registerValidators(validatorRegistrations));
  }

  @Override
  public SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorsLiveness(
      final List<UInt64> validatorIndices, final UInt64 epoch) {
    return sendRequest(() -> typeDefClient.sendValidatorsLiveness(epoch, validatorIndices));
  }

  @Override
  public SafeFuture<Optional<List<BeaconCommitteeSelectionProof>>> getBeaconCommitteeSelectionProof(
      final List<BeaconCommitteeSelectionProof> requests) {
    return sendRequest(() -> typeDefClient.getBeaconCommitteeSelectionProof(requests));
  }

  @Override
  public SafeFuture<Optional<List<SyncCommitteeSelectionProof>>> getSyncCommitteeSelectionProof(
      final List<SyncCommitteeSelectionProof> requests) {
    return sendRequest(() -> typeDefClient.getSyncCommitteeSelectionProof(requests));
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
      final boolean usePostValidatorsEndpoint,
      final AsyncRunner asyncRunner,
      final boolean attestationsV2ApisEnabled) {
    final OkHttpValidatorTypeDefClient typeDefClient =
        new OkHttpValidatorTypeDefClient(
            httpClient, endpoint, spec, preferSszBlockEncoding, attestationsV2ApisEnabled);
    return new RemoteValidatorApiHandler(
        endpoint, typeDefClient, asyncRunner, usePostValidatorsEndpoint);
  }
}

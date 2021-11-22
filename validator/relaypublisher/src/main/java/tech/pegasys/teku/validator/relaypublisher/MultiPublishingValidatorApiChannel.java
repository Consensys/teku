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

package tech.pegasys.teku.validator.relaypublisher;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.merge.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi;

public class MultiPublishingValidatorApiChannel implements ValidatorApiChannel {

  private final ValidatorApiChannel delegate;
  final List<AdditionalPublisherApi> additionalPublisherApis;

  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger logger;

  MultiPublishingValidatorApiChannel(
      final ValidatorApiChannel validatorApiChannel,
      final List<AdditionalPublisherApi> additionalPublisherApis) {
    this(validatorApiChannel, additionalPublisherApis, LogManager.getLogger());
  }

  MultiPublishingValidatorApiChannel(
      final ValidatorApiChannel validatorApiChannel,
      final List<AdditionalPublisherApi> additionalPublisherApis,
      final Logger logger) {
    this.delegate = validatorApiChannel;
    this.additionalPublisherApis = additionalPublisherApis;
    this.logger = logger;
  }

  public static MultiPublishingValidatorApiChannel create(
      final ServiceConfig serviceConfig,
      final AsyncRunner asyncRunner,
      final Spec spec,
      final boolean useIndependentAttestationTiming,
      final boolean generateEarlyAttestations,
      final ValidatorApiChannel validatorApiChannel,
      final List<URI> additionalPublishingUrls) {
    final List<AdditionalPublisherApi> additionalPublishingApis = new ArrayList<>();
    for (final URI uri : additionalPublishingUrls) {
      additionalPublishingApis.add(
          new AdditionalPublisherApi(
              uri,
              RemoteBeaconNodeApi.create(
                  serviceConfig,
                  asyncRunner,
                  uri,
                  spec,
                  useIndependentAttestationTiming,
                  generateEarlyAttestations)));
    }
    return new MultiPublishingValidatorApiChannel(
        validatorApiChannel, additionalPublishingApis, LogManager.getLogger());
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return delegate.getGenesisData();
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys) {
    return delegate.getValidatorIndices(publicKeys);
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      final Collection<BLSPublicKey> validatorIdentifiers) {
    return delegate.getValidatorStatuses(validatorIdentifiers);
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    return delegate.getAttestationDuties(epoch, validatorIndices);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    return delegate.getSyncCommitteeDuties(epoch, validatorIndices);
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(final UInt64 epoch) {
    return delegate.getProposerDuties(epoch);
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UInt64 slot, final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    return delegate.createUnsignedBlock(slot, randaoReveal, graffiti);
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return delegate.createAttestationData(slot, committeeIndex);
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    return delegate.createAggregate(slot, attestationHashTreeRoot);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    return delegate.createSyncCommitteeContribution(slot, subcommitteeIndex, beaconBlockRoot);
  }

  @Override
  public void subscribeToBeaconCommittee(final List<CommitteeSubscriptionRequest> requests) {
    delegate.subscribeToBeaconCommittee(requests);
  }

  @Override
  public void subscribeToSyncCommitteeSubnets(
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    delegate.subscribeToSyncCommitteeSubnets(subscriptions);
  }

  @Override
  public void subscribeToPersistentSubnets(final Set<SubnetSubscription> subnetSubscriptions) {
    delegate.subscribeToPersistentSubnets(subnetSubscriptions);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(
      final List<Attestation> attestations) {
    final SafeFuture<List<SubmitDataError>> future = delegate.sendSignedAttestations(attestations);

    for (AdditionalPublisherApi api : additionalPublisherApis) {
      api.sendSignedAttestations(attestations)
          .finish(error -> logPublishError("attestations", api.getSanitizedUrl(), error));
    }

    return future;
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    final SafeFuture<List<SubmitDataError>> future =
        delegate.sendAggregateAndProofs(aggregateAndProofs);
    for (AdditionalPublisherApi api : additionalPublisherApis) {
      api.sendAggregateAndProofs(aggregateAndProofs)
          .finish(error -> logPublishError("aggregateAndProofs", api.getSanitizedUrl(), error));
    }
    return future;
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(final SignedBeaconBlock block) {
    final SafeFuture<SendSignedBlockResult> future = delegate.sendSignedBlock(block);
    for (AdditionalPublisherApi api : additionalPublisherApis) {
      api.sendSignedBlock(block)
          .finish(error -> logPublishError("block", api.getSanitizedUrl(), error));
    }

    return future;
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    final SafeFuture<List<SubmitDataError>> future =
        delegate.sendSyncCommitteeMessages(syncCommitteeMessages);

    // Sync Committee Messages can print 'failed to produce' if the main publish is slow,
    // so don't publish to other nodes until it's been published by the primary
    future.finish(
        () -> {
          for (AdditionalPublisherApi api : additionalPublisherApis) {
            api.sendSyncCommitteeMessages(syncCommitteeMessages)
                .finish(
                    error ->
                        logPublishError("syncCommitteeMessages", api.getSanitizedUrl(), error));
          }
        },
        logger::error);

    return future;
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    final SafeFuture<Void> future =
        delegate.sendSignedContributionAndProofs(signedContributionAndProofs);
    for (AdditionalPublisherApi api : additionalPublisherApis) {
      api.sendSignedContributionAndProofs(signedContributionAndProofs)
          .finish(
              error ->
                  logPublishError("signedContributionAndProofs", api.getSanitizedUrl(), error));
    }

    return future;
  }

  @Override
  public void prepareBeaconProposer(
      Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    delegate.prepareBeaconProposer(beaconPreparableProposers);
  }

  private void logPublishError(
      final String actionDescription, final String sanitizedUrl, final Throwable error) {
    logger.warn(
        "Failed to send {} to remote publishing host ({}): {}",
        actionDescription,
        sanitizedUrl,
        error.getMessage());
  }
}

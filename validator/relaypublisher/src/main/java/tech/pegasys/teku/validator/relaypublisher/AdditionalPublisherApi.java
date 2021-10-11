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
import java.util.Collection;
import java.util.List;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;

public class AdditionalPublisherApi {
  private final String sanitizedUrl;
  private final BeaconNodeApi delegate;

  public AdditionalPublisherApi(final URI uri, final BeaconNodeApi delegate) {
    this.sanitizedUrl = UrlSanitizer.sanitizePotentialUrl(uri.toString());
    this.delegate = delegate;
  }

  public String getSanitizedUrl() {
    return sanitizedUrl;
  }

  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(
      final List<Attestation> attestations) {
    return delegate.getValidatorApi().sendSignedAttestations(attestations);
  }

  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return delegate.getValidatorApi().sendAggregateAndProofs(aggregateAndProofs);
  }

  public SafeFuture<SendSignedBlockResult> sendSignedBlock(final SignedBeaconBlock block) {
    return delegate.getValidatorApi().sendSignedBlock(block);
  }

  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return delegate.getValidatorApi().sendSyncCommitteeMessages(syncCommitteeMessages);
  }

  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    return delegate.getValidatorApi().sendSignedContributionAndProofs(signedContributionAndProofs);
  }
}

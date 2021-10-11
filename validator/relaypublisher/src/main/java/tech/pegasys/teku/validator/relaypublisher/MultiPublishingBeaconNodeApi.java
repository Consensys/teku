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
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;
import tech.pegasys.teku.validator.eventadapter.InProcessBeaconNodeApi;
import tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi;

public class MultiPublishingBeaconNodeApi implements BeaconNodeApi {

  private final BeaconNodeApi delegate;
  private final ValidatorApiChannel validatorApiChannel;

  MultiPublishingBeaconNodeApi(
      final BeaconNodeApi delegate, final ValidatorApiChannel validatorApiChannel) {
    this.delegate = delegate;
    this.validatorApiChannel = validatorApiChannel;
  }

  public static BeaconNodeApi create(
      final ServiceConfig serviceConfig,
      final AsyncRunner asyncRunner,
      final Optional<URI> beaconNodeApiEndpoint,
      final Spec spec,
      final boolean useIndependentAttestationTiming,
      final boolean generateEarlyAttestations,
      final List<URI> additionalPublishingUrls) {
    final BeaconNodeApi beaconNodeApi =
        beaconNodeApiEndpoint
            .map(
                endpoint ->
                    RemoteBeaconNodeApi.create(
                        serviceConfig,
                        asyncRunner,
                        endpoint,
                        spec,
                        useIndependentAttestationTiming,
                        generateEarlyAttestations))
            .orElseGet(
                () ->
                    InProcessBeaconNodeApi.create(
                        serviceConfig,
                        asyncRunner,
                        useIndependentAttestationTiming,
                        generateEarlyAttestations,
                        spec));

    final ValidatorApiChannel validatorApiChannel =
        MultiPublishingValidatorApiChannel.create(
            serviceConfig,
            asyncRunner,
            spec,
            useIndependentAttestationTiming,
            generateEarlyAttestations,
            beaconNodeApi.getValidatorApi(),
            additionalPublishingUrls);
    return new MultiPublishingBeaconNodeApi(beaconNodeApi, validatorApiChannel);
  }

  @Override
  public SafeFuture<Void> subscribeToEvents() {
    return delegate.subscribeToEvents();
  }

  @Override
  public SafeFuture<Void> unsubscribeFromEvents() {
    return delegate.unsubscribeFromEvents();
  }

  @Override
  public ValidatorApiChannel getValidatorApi() {
    return validatorApiChannel;
  }
}

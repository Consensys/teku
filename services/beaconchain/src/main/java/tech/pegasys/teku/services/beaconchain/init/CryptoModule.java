/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.BeaconAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.statetransition.validation.signatures.AggregatingSignatureVerificationService;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;

@Module
public interface CryptoModule {

  @Provides
  @Singleton
  static SignatureVerificationService signatureVerificationService(
      final P2PConfig p2PConfig,
      final MetricsSystem metricsSystem,
      final AsyncRunnerFactory asyncRunnerFactory,
      @BeaconAsyncRunner final AsyncRunner beaconAsyncRunner) {
    return new AggregatingSignatureVerificationService(
        metricsSystem,
        asyncRunnerFactory,
        beaconAsyncRunner,
        p2PConfig.getBatchVerifyMaxThreads(),
        p2PConfig.getBatchVerifyQueueCapacity(),
        p2PConfig.getBatchVerifyMaxBatchSize(),
        p2PConfig.isBatchVerifyStrictThreadLimitEnabled());
  }

  @Provides
  @Singleton
  static KZG kzg(final Eth2NetworkConfiguration eth2NetworkConfig, final Spec spec) {
    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      KZG kzg = KZG.getInstance();
      final String trustedSetupFile =
          eth2NetworkConfig
              .getTrustedSetup()
              .orElseThrow(
                  () ->
                      new InvalidConfigurationException(
                          "Trusted setup should be configured when Deneb is enabled"));
      kzg.loadTrustedSetup(trustedSetupFile);
      return kzg;
    } else {
      return KZG.NOOP;
    }
  }
}

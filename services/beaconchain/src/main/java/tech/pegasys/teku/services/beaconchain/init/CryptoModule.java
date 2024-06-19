package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
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

import javax.inject.Singleton;

@Module
public interface CryptoModule {

  @Provides
  @Singleton
  static SignatureVerificationService signatureVerificationService(
      P2PConfig p2PConfig,
      MetricsSystem metricsSystem,
      AsyncRunnerFactory asyncRunnerFactory,
      @BeaconAsyncRunner AsyncRunner beaconAsyncRunner) {
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
  static KZG provideKzg(Eth2NetworkConfiguration eth2NetworkConfig, Spec spec) {
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

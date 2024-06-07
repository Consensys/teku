package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.synccommittee.SignedContributionAndProofValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessageValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeStateUtils;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttesterSlashingValidator;
import tech.pegasys.teku.statetransition.validation.BlockGossipValidator;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.ProposerSlashingValidator;
import tech.pegasys.teku.statetransition.validation.SignedBlsToExecutionChangeValidator;
import tech.pegasys.teku.statetransition.validation.VoluntaryExitValidator;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.storage.client.RecentChainData;

import javax.inject.Singleton;

@Module
public interface VerifyModule {
  @Provides
  @Singleton
  static GossipValidationHelper provideGossipValidationHelper(
      Spec spec, RecentChainData recentChainData) {
    return new GossipValidationHelper(spec, recentChainData);
  }

  @Provides
  @Singleton
  static AttesterSlashingValidator provideAttesterSlashingValidator(
      Spec spec, RecentChainData recentChainData) {
    return new AttesterSlashingValidator(recentChainData, spec);
  }

  @Provides
  @Singleton
  static ProposerSlashingValidator provideProposerSlashingValidator(
      Spec spec, RecentChainData recentChainData) {
    return new ProposerSlashingValidator(spec, recentChainData);
  }

  @Provides
  @Singleton
  static VoluntaryExitValidator provideVoluntaryExitValidator(
      Spec spec, RecentChainData recentChainData) {
    return new VoluntaryExitValidator(spec, recentChainData);
  }

  @Provides
  @Singleton
  static SignedBlsToExecutionChangeValidator provideSignedBlsToExecutionChangeValidator(
      Spec spec,
      TimeProvider timeProvider,
      RecentChainData recentChainData,
      SignatureVerificationService signatureVerificationService) {
    return new SignedBlsToExecutionChangeValidator(
        spec, timeProvider, recentChainData, signatureVerificationService);
  }

  @Provides
  @Singleton
  static MergeTransitionBlockValidator provideMergeTransitionBlockValidator(
      Spec spec, RecentChainData recentChainData, ExecutionLayerChannel executionLayer) {
    return new MergeTransitionBlockValidator(spec, recentChainData, executionLayer);
  }

  @Provides
  @Singleton
  static AttestationValidator attestationValidator(
      Spec spec,
      RecentChainData recentChainData,
      SignatureVerificationService signatureVerificationService,
      MetricsSystem metricsSystem) {
    return new AttestationValidator(
        spec, recentChainData, signatureVerificationService, metricsSystem);
  }

  @Provides
  @Singleton
  static AggregateAttestationValidator aggregateAttestationValidator(
      Spec spec,
      AttestationValidator attestationValidator,
      SignatureVerificationService signatureVerificationService) {
    return new AggregateAttestationValidator(
        spec, attestationValidator, signatureVerificationService);
  }

  @Provides
  @Singleton
  static SyncCommitteeStateUtils syncCommitteeStateUtils(
      Spec spec, RecentChainData recentChainData) {
    return new SyncCommitteeStateUtils(spec, recentChainData);
  }

  @Provides
  @Singleton
  static SignedContributionAndProofValidator signedContributionAndProofValidator(
      Spec spec,
      TimeProvider timeProvider,
      RecentChainData recentChainData,
      SyncCommitteeStateUtils syncCommitteeStateUtils,
      SignatureVerificationService signatureVerificationService) {
    return new SignedContributionAndProofValidator(
        spec, recentChainData, syncCommitteeStateUtils, timeProvider, signatureVerificationService);
  }

  @Provides
  @Singleton
  static SyncCommitteeMessageValidator syncCommitteeMessageValidator(
      Spec spec,
      TimeProvider timeProvider,
      RecentChainData recentChainData,
      SyncCommitteeStateUtils syncCommitteeStateUtils,
      SignatureVerificationService signatureVerificationService) {
    return new SyncCommitteeMessageValidator(
        spec, recentChainData, syncCommitteeStateUtils, signatureVerificationService, timeProvider);
  }

  @Provides
  @Singleton
  static BlockGossipValidator blockGossipValidator(
      Spec spec,
      GossipValidationHelper gossipValidationHelper,
      ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher) {
    return new BlockGossipValidator(
        spec, gossipValidationHelper, receivedBlockEventsChannelPublisher);
  }

  @Provides
  @Singleton
  static BlockValidator blockValidator(BlockGossipValidator blockGossipValidator) {
    return new BlockValidator(blockGossipValidator);
  }
}

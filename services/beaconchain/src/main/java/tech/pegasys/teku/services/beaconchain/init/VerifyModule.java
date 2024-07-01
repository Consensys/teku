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

@Module
public interface VerifyModule {
  @Provides
  @Singleton
  static GossipValidationHelper gossipValidationHelper(
      final Spec spec, final RecentChainData recentChainData) {
    return new GossipValidationHelper(spec, recentChainData);
  }

  @Provides
  @Singleton
  static AttesterSlashingValidator attesterSlashingValidator(
      final Spec spec, final RecentChainData recentChainData) {
    return new AttesterSlashingValidator(recentChainData, spec);
  }

  @Provides
  @Singleton
  static ProposerSlashingValidator proposerSlashingValidator(
      final Spec spec, final RecentChainData recentChainData) {
    return new ProposerSlashingValidator(spec, recentChainData);
  }

  @Provides
  @Singleton
  static VoluntaryExitValidator voluntaryExitValidator(
      final Spec spec, final RecentChainData recentChainData) {
    return new VoluntaryExitValidator(spec, recentChainData);
  }

  @Provides
  @Singleton
  static SignedBlsToExecutionChangeValidator signedBlsToExecutionChangeValidator(
      final Spec spec,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final SignatureVerificationService signatureVerificationService) {
    return new SignedBlsToExecutionChangeValidator(
        spec, timeProvider, recentChainData, signatureVerificationService);
  }

  @Provides
  @Singleton
  static MergeTransitionBlockValidator mergeTransitionBlockValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer) {
    return new MergeTransitionBlockValidator(spec, recentChainData, executionLayer);
  }

  @Provides
  @Singleton
  static AttestationValidator attestationValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final SignatureVerificationService signatureVerificationService,
      final MetricsSystem metricsSystem) {
    return new AttestationValidator(
        spec, recentChainData, signatureVerificationService, metricsSystem);
  }

  @Provides
  @Singleton
  static AggregateAttestationValidator aggregateAttestationValidator(
      final Spec spec,
      final AttestationValidator attestationValidator,
      final SignatureVerificationService signatureVerificationService) {
    return new AggregateAttestationValidator(
        spec, attestationValidator, signatureVerificationService);
  }

  @Provides
  @Singleton
  static SyncCommitteeStateUtils syncCommitteeStateUtils(
      final Spec spec, final RecentChainData recentChainData) {
    return new SyncCommitteeStateUtils(spec, recentChainData);
  }

  @Provides
  @Singleton
  static SignedContributionAndProofValidator signedContributionAndProofValidator(
      final Spec spec,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final SyncCommitteeStateUtils syncCommitteeStateUtils,
      final SignatureVerificationService signatureVerificationService) {
    return new SignedContributionAndProofValidator(
        spec, recentChainData, syncCommitteeStateUtils, timeProvider, signatureVerificationService);
  }

  @Provides
  @Singleton
  static SyncCommitteeMessageValidator syncCommitteeMessageValidator(
      final Spec spec,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final SyncCommitteeStateUtils syncCommitteeStateUtils,
      final SignatureVerificationService signatureVerificationService) {
    return new SyncCommitteeMessageValidator(
        spec, recentChainData, syncCommitteeStateUtils, signatureVerificationService, timeProvider);
  }

  @Provides
  @Singleton
  static BlockGossipValidator blockGossipValidator(
      final Spec spec,
      final GossipValidationHelper gossipValidationHelper,
      final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher) {
    return new BlockGossipValidator(
        spec, gossipValidationHelper, receivedBlockEventsChannelPublisher);
  }

  @Provides
  @Singleton
  static BlockValidator blockValidator(final BlockGossipValidator blockGossipValidator) {
    return new BlockValidator(blockGossipValidator);
  }
}

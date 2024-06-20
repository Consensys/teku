package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.events.EventChannelSubscriber;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.BeaconAsyncRunner;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.OperationPoolAsyncRunner;
import tech.pegasys.teku.services.beaconchain.init.SpecModule.SchemaSupplier;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.MappedOperationPool;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.SimpleOperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.block.FailedExecutionPool;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.synccommittee.SignedContributionAndProofValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessagePool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessageValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeStateUtils;
import tech.pegasys.teku.statetransition.util.BlockBlobSidecarsTrackersPoolImpl;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.AttesterSlashingValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ProposerSlashingValidator;
import tech.pegasys.teku.statetransition.validation.SignedBlsToExecutionChangeValidator;
import tech.pegasys.teku.statetransition.validation.VoluntaryExitValidator;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import static tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool.DEFAULT_MAXIMUM_ATTESTATION_COUNT;

@Module
public interface PoolAndCachesModule {

  @Qualifier
  @interface InvalidBlockRoots {}

  @Qualifier
  @interface InvalidBlobSidecarRoots {}

  @Provides
  @Singleton
  static PoolFactory poolFactory(MetricsSystem metricsSystem) {
    return new PoolFactory(metricsSystem);
  }

  @Provides
  @Singleton
  static PendingPool<SignedBeaconBlock> pendingBlocksPool(
      Spec spec,
      PoolFactory poolFactory,
      EventChannelSubscriber<FinalizedCheckpointChannel> channelSubscriber) {
    PendingPool<SignedBeaconBlock> pool = poolFactory.createPendingPoolForBlocks(spec);
    channelSubscriber.subscribe(pool);
    return pool;
  }

  @Provides
  @Singleton
  @InvalidBlockRoots
  static Map<Bytes32, BlockImportResult> invalidBlockRoots() {
    return LimitedMap.createSynchronizedLRU(500);
  }

  @Provides
  @Singleton
  @InvalidBlobSidecarRoots
  static Map<Bytes32, InternalValidationResult> invalidBlobSidecarRoots() {
    return LimitedMap.createSynchronizedLRU(500);
  }

  @Provides
  @Singleton
  static BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool(
      Spec spec,
      @BeaconAsyncRunner AsyncRunner beaconAsyncRunner,
      TimeProvider timeProvider,
      RecentChainData recentChainData,
      BlockImportChannel blockImportChannel,
      PoolFactory poolFactory,
      EventChannelSubscriber<FinalizedCheckpointChannel> finalizedCheckpointChannelSubscriber) {
    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      final BlockBlobSidecarsTrackersPoolImpl pool =
          poolFactory.createPoolForBlockBlobSidecarsTrackers(
              blockImportChannel, spec, timeProvider, beaconAsyncRunner, recentChainData);
      finalizedCheckpointChannelSubscriber.subscribe(pool);
      return pool;
    } else {
      return BlockBlobSidecarsTrackersPool.NOOP;
    }
  }

  @Provides
  @Singleton
  static OperationPool<AttesterSlashing> attesterSlashingPool(
      MetricsSystem metricsSystem,
      SchemaSupplier<BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier,
      BlockImporter blockImporter,
      AttesterSlashingValidator attesterSlashingValidator,
      ForkChoice forkChoice) {
    OperationPool<AttesterSlashing> attesterSlashingPool =
        new SimpleOperationPool<>(
            "AttesterSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getAttesterSlashingsSchema),
            attesterSlashingValidator,
            // Prioritise slashings that include more validators at a time
            Comparator.<AttesterSlashing>comparingInt(
                    slashing -> slashing.getIntersectingValidatorIndices().size())
                .reversed());
    blockImporter.subscribeToVerifiedBlockAttesterSlashings(attesterSlashingPool::removeAll);
    attesterSlashingPool.subscribeOperationAdded(forkChoice::onAttesterSlashing);
    return attesterSlashingPool;
  }

  @Provides
  @Singleton
  static OperationPool<ProposerSlashing> proposerSlashingPool(
      MetricsSystem metricsSystem,
      SchemaSupplier<BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier,
      BlockImporter blockImporter,
      ProposerSlashingValidator validator) {
    SimpleOperationPool<ProposerSlashing> proposerSlashingPool =
        new SimpleOperationPool<>(
            "ProposerSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
            validator);
    blockImporter.subscribeToVerifiedBlockProposerSlashings(proposerSlashingPool::removeAll);
    return proposerSlashingPool;
  }

  @Provides
  @Singleton
  static OperationPool<SignedVoluntaryExit> voluntaryExitPool(
      MetricsSystem metricsSystem,
      @OperationPoolAsyncRunner AsyncRunner operationPoolAsyncRunner,
      TimeProvider timeProvider,
      SchemaSupplier<BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier,
      BlockImporter blockImporter,
      VoluntaryExitValidator validator) {
    MappedOperationPool<SignedVoluntaryExit> voluntaryExitPool =
        new MappedOperationPool<>(
            "VoluntaryExitPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getVoluntaryExitsSchema),
            validator,
            operationPoolAsyncRunner,
            timeProvider);
    blockImporter.subscribeToVerifiedBlockVoluntaryExits(voluntaryExitPool::removeAll);
    return voluntaryExitPool;
  }

  @Provides
  @Singleton
  static OperationPool<SignedBlsToExecutionChange> signedBlsToExecutionChangePool(
      MetricsSystem metricsSystem,
      @OperationPoolAsyncRunner AsyncRunner operationPoolAsyncRunner,
      TimeProvider timeProvider,
      SchemaSupplier<BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier,
      BlockImporter blockImporter,
      SignedBlsToExecutionChangeValidator validator) {
    OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool =
        new MappedOperationPool<>(
            "SignedBlsToExecutionChangePool",
            metricsSystem,
            beaconBlockSchemaSupplier
                .andThen(BeaconBlockBodySchema::toVersionCapella)
                .andThen(Optional::orElseThrow)
                .andThen(BeaconBlockBodySchemaCapella::getBlsToExecutionChangesSchema),
            validator,
            operationPoolAsyncRunner,
            timeProvider);
    blockImporter.subscribeToVerifiedBlockBlsToExecutionChanges(
        blsToExecutionChangePool::removeAll);
    return blsToExecutionChangePool;
  }

  @Provides
  @Singleton
  static PendingPool<ValidatableAttestation> pendingAttestationPool(
      Spec spec,
      PoolFactory poolFactory,
      EventChannelSubscriber<FinalizedCheckpointChannel> finalizedCheckpointChannelSubscriber) {
    final PendingPool<ValidatableAttestation> pendingAttestations =
        poolFactory.createPendingPoolForAttestations(spec);
    finalizedCheckpointChannelSubscriber.subscribe(pendingAttestations);
    return pendingAttestations;
  }

  @Provides
  @Singleton
  static SyncCommitteeContributionPool syncCommitteeContributionPool(
      Spec spec,
      SignedContributionAndProofValidator signedContributionAndProofValidator,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber) {
    SyncCommitteeContributionPool syncCommitteeContributionPool =
        new SyncCommitteeContributionPool(spec, signedContributionAndProofValidator);

    slotEventsChannelSubscriber.subscribe(syncCommitteeContributionPool);
    return syncCommitteeContributionPool;
  }

  @Provides
  @Singleton
  static SyncCommitteeMessagePool syncCommitteeMessagePool(
      Spec spec,
      SyncCommitteeMessageValidator syncCommitteeMessageValidator,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber) {
    SyncCommitteeMessagePool syncCommitteeMessagePool =
        new SyncCommitteeMessagePool(spec, syncCommitteeMessageValidator);
    slotEventsChannelSubscriber.subscribe(syncCommitteeMessagePool);
    return syncCommitteeMessagePool;
  }

  @Provides
  @Singleton
  static AggregatingAttestationPool aggregatingAttestationPool(
      Spec spec,
      RecentChainData recentChainData,
      MetricsSystem metricsSystem,
      BlockImporter blockImporter,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber) {
    AggregatingAttestationPool attestationPool =
        new AggregatingAttestationPool(
            spec, recentChainData, metricsSystem, DEFAULT_MAXIMUM_ATTESTATION_COUNT);
    slotEventsChannelSubscriber.subscribe(attestationPool);
    blockImporter.subscribeToVerifiedBlockAttestations(
        attestationPool::onAttestationsIncludedInBlock);
    return attestationPool;
  }

  @Provides
  @Singleton
  static FailedExecutionPool failedExecutionPool(
      BlockManager blockManager, @BeaconAsyncRunner AsyncRunner beaconAsyncRunner) {
    return new FailedExecutionPool(blockManager, beaconAsyncRunner);
  }
}

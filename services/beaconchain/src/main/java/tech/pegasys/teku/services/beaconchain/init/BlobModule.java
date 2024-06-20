package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import java.util.Map;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannelSubscriber;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.BeaconAsyncRunner;
import tech.pegasys.teku.services.beaconchain.init.PoolAndCachesModule.InvalidBlobSidecarRoots;
import tech.pegasys.teku.services.beaconchain.init.PoolAndCachesModule.InvalidBlockRoots;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManagerImpl;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.validation.BlobSidecarGossipValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

@Module
public interface BlobModule {

  @Provides
  @Singleton
  static BlobSidecarGossipValidator blobSidecarGossipValidator(
      Spec spec,
      KZG kzg,
      @InvalidBlockRoots Map<Bytes32, BlockImportResult> invalidBlockRoots,
      GossipValidationHelper gossipValidationHelper
  ) {
    final MiscHelpersDeneb miscHelpers =
        MiscHelpersDeneb.required(spec.forMilestone(SpecMilestone.DENEB).miscHelpers());
    return BlobSidecarGossipValidator.create(
        spec, invalidBlockRoots, gossipValidationHelper, miscHelpers, kzg);
  }
  
  
  @Provides
  @Singleton
  static BlobSidecarManager blobSidecarManager(
      Spec spec,
      KZG kzg,
      @BeaconAsyncRunner AsyncRunner beaconAsyncRunner,
      RecentChainData recentChainData,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      BlobSidecarGossipValidator blobSidecarGossipValidator,
      @InvalidBlobSidecarRoots Map<Bytes32, InternalValidationResult> invalidBlobSidecarRoots,
      FutureItems<BlobSidecar> futureBlobSidecars
  ) {
    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      final BlobSidecarManagerImpl blobSidecarManagerImpl =
          new BlobSidecarManagerImpl(
              spec,
              beaconAsyncRunner,
              recentChainData,
              blockBlobSidecarsTrackersPool,
              blobSidecarGossipValidator,
              kzg,
              futureBlobSidecars,
              invalidBlobSidecarRoots);
      slotEventsChannelSubscriber.subscribe(blobSidecarManagerImpl);

      return blobSidecarManagerImpl;
    } else {
      return BlobSidecarManager.NOOP;
    }
  }
}

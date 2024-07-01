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
      final Spec spec,
      final KZG kzg,
      @InvalidBlockRoots final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final GossipValidationHelper gossipValidationHelper) {
    final MiscHelpersDeneb miscHelpers =
        MiscHelpersDeneb.required(spec.forMilestone(SpecMilestone.DENEB).miscHelpers());
    return BlobSidecarGossipValidator.create(
        spec, invalidBlockRoots, gossipValidationHelper, miscHelpers, kzg);
  }

  @Provides
  @Singleton
  static BlobSidecarManager blobSidecarManager(
      final Spec spec,
      final KZG kzg,
      @BeaconAsyncRunner final AsyncRunner beaconAsyncRunner,
      final RecentChainData recentChainData,
      final EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final BlobSidecarGossipValidator blobSidecarGossipValidator,
      @InvalidBlobSidecarRoots final Map<Bytes32, InternalValidationResult> invalidBlobSidecarRoots,
      final FutureItems<BlobSidecar> futureBlobSidecars) {
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

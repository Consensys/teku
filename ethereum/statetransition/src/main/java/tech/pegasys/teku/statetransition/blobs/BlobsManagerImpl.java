/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.statetransition.blobs;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceBlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobsManagerImpl implements BlobsManager, FinalizedCheckpointChannel {

  private final Map<Bytes32, BlobsSidecar> blobsSidecarByBlockRoot = new ConcurrentHashMap<>();
  private final Spec spec;
  private final RecentChainData recentChainData;

  public BlobsManagerImpl(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  @Override
  public SafeFuture<Void> importBlobs(BlobsSidecar blobsSidecar) {
    return SafeFuture.of(
        () -> {
          blobsSidecarByBlockRoot.put(blobsSidecar.getBeaconBlockRoot(), blobsSidecar);
          return null;
        });
  }

  @Override
  public SafeFuture<Optional<BlobsSidecar>> getBlobsByBlockRoot(Bytes32 blockRoot) {
    return SafeFuture.completedFuture(Optional.ofNullable(blobsSidecarByBlockRoot.get(blockRoot)));
  }

  @Override
  public SafeFuture<Void> discardBlobsByBlockRoot(Bytes32 blockRoot) {
    return SafeFuture.of(
        () -> {
          blobsSidecarByBlockRoot.remove(blockRoot);
          return null;
        });
  }

  @Override
  public BlobsSidecarAvailabilityChecker createAvailabilityChecker(final SignedBeaconBlock block) {
    return new ForkChoiceBlobsSidecarAvailabilityChecker(
        spec.atSlot(block.getSlot()), recentChainData, block, this);
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint checkpoint, boolean fromOptimisticBlock) {}
}

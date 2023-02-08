/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.statetransition.blobs.BlobsSidecarManager;

public class BlockAndBlobsSidecarMatcher {

  private static final Logger LOG = LogManager.getLogger();

  private final Map<UInt64, SignedBeaconBlock> blocksBySlot = Maps.newConcurrentMap();
  private final Map<UInt64, BlobsSidecar> blobsSidecarsBySlot = Maps.newConcurrentMap();

  private final BlobsSidecarManager blobsSidecarManager;
  private final BiFunction<SignedBeaconBlock, BlobsSidecar, SafeFuture<Void>> actionOnMatching;
  private final Function<SignedBeaconBlock, SafeFuture<Void>> actionOnMatchingNotRequired;

  public BlockAndBlobsSidecarMatcher(
      final BlobsSidecarManager blobsSidecarManager,
      final BiFunction<SignedBeaconBlock, BlobsSidecar, SafeFuture<Void>> actionOnMatching,
      final Function<SignedBeaconBlock, SafeFuture<Void>> actionOnMatchingNotRequired) {
    this.blobsSidecarManager = blobsSidecarManager;
    this.actionOnMatching = actionOnMatching;
    this.actionOnMatchingNotRequired = actionOnMatchingNotRequired;
  }

  public SafeFuture<Void> recordBlock(final SignedBeaconBlock block) {
    final UInt64 slot = block.getSlot();
    if (!isSidecarRequired(block, slot)) {
      LOG.trace("Block for slot {} does not require sidecar", slot);
      return actionOnMatchingNotRequired.apply(block);
    }
    blocksBySlot.put(slot, block);
    final BlobsSidecar blobsSidecar = blobsSidecarsBySlot.remove(slot);
    if (blobsSidecar != null) {
      LOG.trace("Matched block and blobs sidecar for slot {}", slot);
      return actionOnMatching.apply(blocksBySlot.remove(slot), blobsSidecar);
    }
    return SafeFuture.COMPLETE;
  }

  public SafeFuture<Void> recordBlobsSidecar(final BlobsSidecar blobsSidecar) {
    final UInt64 slot = blobsSidecar.getBeaconBlockSlot();
    blobsSidecarsBySlot.put(slot, blobsSidecar);
    final SignedBeaconBlock block = blocksBySlot.remove(slot);
    if (block != null) {
      LOG.trace("Matched block and blobs sidecar for slot {}", slot);
      return actionOnMatching.apply(block, blobsSidecarsBySlot.remove(slot));
    }
    return SafeFuture.COMPLETE;
  }

  private boolean isSidecarRequired(final SignedBeaconBlock block, final UInt64 slot) {
    final boolean blockHasKzgCommitments =
        block
            .getMessage()
            .getBody()
            .toVersionDeneb()
            .map(BeaconBlockBodyDeneb::getBlobKzgCommitments)
            .map(kzgCommitments -> !kzgCommitments.isEmpty())
            .orElse(false);
    return blockHasKzgCommitments && blobsSidecarManager.isStorageOfBlobsSidecarRequired(slot);
  }

  public void clearCache() {
    blocksBySlot.clear();
    blobsSidecarsBySlot.clear();
  }
}

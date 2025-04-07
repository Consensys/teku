/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.storage.api;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

public interface BlobSidecarsArchiveChannel extends ChannelInterface {

  void archive(SlotAndBlockRoot slotAndBlockRoot, List<BlobSidecar> blobSidecars);

  SafeFuture<Optional<List<BlobSidecar>>> retrieve(Bytes32 blockRoot, Optional<UInt64> maybeSlot);

  SafeFuture<Optional<List<BlobSidecar>>> retrieve(UInt64 slot);

  default SafeFuture<Optional<List<BlobSidecar>>> retrieve(final Bytes32 blockRoot) {
    return retrieve(blockRoot, Optional.empty());
  }

  default SafeFuture<Optional<List<BlobSidecar>>> retrieve(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return retrieve(slotAndBlockRoot.getBlockRoot(), Optional.of(slotAndBlockRoot.getSlot()));
  }
}

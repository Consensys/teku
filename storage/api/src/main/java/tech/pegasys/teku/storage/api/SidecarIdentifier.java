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

import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;

public record SidecarIdentifier(
    Optional<SlotAndBlockRootAndBlobIndex> blobSidecarIdentifier,
    Optional<Pair<SlotAndBlockRoot, UInt64>> dataColumnSidecarIdentifierAndBlobIndex,
    boolean canonical) {
  public SidecarIdentifier(
      final Optional<SlotAndBlockRootAndBlobIndex> blobSidecarIdentifier,
      final Optional<Pair<SlotAndBlockRoot, UInt64>> dataColumnSidecarIdentifierAndBlobIndex,
      final boolean canonical) {
    if (blobSidecarIdentifier.isPresent() == dataColumnSidecarIdentifierAndBlobIndex.isPresent()) {
      throw new RuntimeException(
          "Either blob sidecar or data column sidecars identifier have to be set");
    }
    this.blobSidecarIdentifier = blobSidecarIdentifier;
    this.dataColumnSidecarIdentifierAndBlobIndex = dataColumnSidecarIdentifierAndBlobIndex;
    this.canonical = canonical;
  }

  boolean isBlobSidecar() {
    return blobSidecarIdentifier.isPresent();
  }

  boolean isDataColumnSidecar() {
    return dataColumnSidecarIdentifierAndBlobIndex.isPresent();
  }
}

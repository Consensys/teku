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

package tech.pegasys.teku.api.schema.deneb;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContentsSchema;

public class BlockContents {

  @JsonProperty("block")
  private final BeaconBlockDeneb beaconBlock;

  @JsonProperty("blob_sidecars")
  private final List<BlobSidecar> blobSidecars;

  public BlockContents(
      @JsonProperty("beacon_block") final BeaconBlockDeneb beaconBlock,
      @JsonProperty("blob_sidecars") final List<BlobSidecar> blobSidecars) {
    this.beaconBlock = beaconBlock;
    this.blobSidecars = blobSidecars;
  }

  public BlockContents(
      final tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents
          blockContents) {
    this.beaconBlock = new BeaconBlockDeneb(blockContents.getBeaconBlock());
    this.blobSidecars =
        blockContents.getBlobSidecars().stream().map(BlobSidecar::new).collect(Collectors.toList());
  }

  public static BlockContents create(
      final tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents
          blockContents) {
    return new BlockContents(blockContents);
  }

  public tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents
      asInternalBlockContents(
          final BlockContentsSchema blockContentsSchema,
          final BlobSidecarSchema blobSidecarSchema,
          final Spec spec) {
    return blockContentsSchema.create(
        beaconBlock.asInternalBeaconBlock(spec),
        blobSidecars.stream()
            .map(blobSidecar -> blobSidecar.asInternalBlobSidecar(blobSidecarSchema))
            .collect(Collectors.toList()));
  }
}

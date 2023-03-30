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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBlockContentsSchema;

public class BlindedBlockContents {

  @JsonProperty("blinded_beacon_block")
  private final BlindedBlockDeneb blindedBeaconBlock;

  @JsonProperty("blinded_blob_sidecars")
  private final BlindedBlobSidecars blindedBlobSidecars;

  public BlindedBlockContents(
      @JsonProperty("blinded_beacon_block") final BlindedBlockDeneb blindedBeaconBlock,
      @JsonProperty("blinded_blob_sidecars") final BlindedBlobSidecars blindedBlobSidecars) {
    this.blindedBeaconBlock = blindedBeaconBlock;
    this.blindedBlobSidecars = blindedBlobSidecars;
  }

  public BlindedBlockContents(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
              .BlindedBlockContents
          blindedBlockContents) {
    this.blindedBeaconBlock = new BlindedBlockDeneb(blindedBlockContents.getSignedBeaconBlock());
    this.blindedBlobSidecars =
        new BlindedBlobSidecars(blindedBlockContents.getBlindedBlobSidecars());
  }

  public static BlindedBlockContents create(
      final BlindedBlockDeneb blindedBlockDeneb, final BlindedBlobSidecars blindedBlobSidecars) {
    return new BlindedBlockContents(blindedBlockDeneb, blindedBlobSidecars);
  }

  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBlockContents
      asInternalBlindedBlockContents(
          final BlindedBlockContentsSchema blindedBlockContentsSchema, final Spec spec) {
    return blindedBlockContentsSchema.create(
        blindedBeaconBlock.asInternalBeaconBlock(spec),
        blindedBlobSidecars.asInternalBlindedBlobSidecars(
            blindedBlockContentsSchema.getBlindedBlobSidecarsSchema()));
  }
}

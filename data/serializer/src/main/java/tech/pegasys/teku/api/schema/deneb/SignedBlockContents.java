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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlockContentsSchema;

public class SignedBlockContents {

  @JsonProperty("signed_beacon_block")
  private final SignedBeaconBlockDeneb signedBeaconBlockDeneb;

  @JsonProperty("signed_blob_sidecars")
  private final SignedBlobSidecars signedBlobSidecars;

  public SignedBlockContents(
      @JsonProperty("signed_beacon_block") final SignedBeaconBlockDeneb signedBeaconBlockDeneb,
      @JsonProperty("signed_blob_sidecars") final SignedBlobSidecars signedBlobSidecars) {
    this.signedBeaconBlockDeneb = signedBeaconBlockDeneb;
    this.signedBlobSidecars = signedBlobSidecars;
  }

  public SignedBlockContents(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
              .SignedBlockContents
          signedBlockContents) {
    this.signedBeaconBlockDeneb =
        new SignedBeaconBlockDeneb(signedBlockContents.getSignedBeaconBlock());
    this.signedBlobSidecars = new SignedBlobSidecars(signedBlockContents.getSignedBlobSidecars());
  }

  public static BlockContents create(
      final tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlockContents
          blockContents) {
    return new BlockContents(blockContents);
  }

  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlockContents
      asInternalSignedBlockContents(
          final SignedBlockContentsSchema signedBlockContentsSchema, final Spec spec) {
    return signedBlockContentsSchema.create(
        signedBeaconBlockDeneb.asInternalSignedBeaconBlock(spec),
        signedBlobSidecars.asInternalSignedBlobSidecars(
            signedBlockContentsSchema.getSignedBlobSidecarsSchema()));
  }
}

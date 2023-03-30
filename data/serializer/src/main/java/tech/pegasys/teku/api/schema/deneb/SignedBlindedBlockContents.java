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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlindedBlockContentsSchema;

public class SignedBlindedBlockContents {

  @JsonProperty("signed_blinded_beacon_block")
  private final SignedBlindedBeaconBlockDeneb signedBlindedBeaconBlockDeneb;

  @JsonProperty("signed_blinded_blob_sidecars")
  private final SignedBlindedBlobSidecars signedBlindedBlobSidecars;

  public SignedBlindedBlockContents(
      @JsonProperty("signed_blinded_beacon_block")
          final SignedBlindedBeaconBlockDeneb signedBlindedBeaconBlockDeneb,
      @JsonProperty("signed_blinded_blob_sidecars")
          final SignedBlindedBlobSidecars signedBlindedBlobSidecars) {
    this.signedBlindedBeaconBlockDeneb = signedBlindedBeaconBlockDeneb;
    this.signedBlindedBlobSidecars = signedBlindedBlobSidecars;
  }

  public SignedBlindedBlockContents(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
              .SignedBlindedBlockContents
          signedBlindedBlockContents) {
    this.signedBlindedBeaconBlockDeneb =
        new SignedBlindedBeaconBlockDeneb(signedBlindedBlockContents.getSignedBeaconBlock());
    this.signedBlindedBlobSidecars =
        new SignedBlindedBlobSidecars(signedBlindedBlockContents.getSignedBlindedBlobSidecars());
  }

  public static SignedBlindedBlockContents create(
      final SignedBlindedBeaconBlockDeneb signedBlindedBeaconBlockDeneb,
      final SignedBlindedBlobSidecars signedBlindedBlobSidecars) {
    return new SignedBlindedBlockContents(signedBlindedBeaconBlockDeneb, signedBlindedBlobSidecars);
  }

  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
          .SignedBlindedBlockContents
      asInternalSignedBlindedBlockContents(
          final SignedBlindedBlockContentsSchema signedBlindedBlockContentsSchema,
          final Spec spec) {
    return signedBlindedBlockContentsSchema.create(
        signedBlindedBeaconBlockDeneb.asInternalSignedBeaconBlock(spec),
        signedBlindedBlobSidecars.asInternalSignedBlindedBlobSidecars(
            signedBlindedBlockContentsSchema.getSignedBlindedBlobSidecarsSchema()));
  }
}

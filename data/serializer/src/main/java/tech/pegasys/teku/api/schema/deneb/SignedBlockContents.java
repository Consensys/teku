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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContentsSchema;

public class SignedBlockContents implements BlockContainer {
  @JsonProperty("signed_block")
  private SignedBeaconBlockDeneb signedBeaconBlockDeneb;

  @JsonProperty("signed_blob_sidecars")
  private List<SignedBlobSidecar> signedBlobSidecars;

  public SignedBlockContents(
      @JsonProperty("signed_beacon_block") final SignedBeaconBlockDeneb signedBeaconBlockDeneb,
      @JsonProperty("signed_blob_sidecars") final List<SignedBlobSidecar> signedBlobSidecars) {
    this.signedBeaconBlockDeneb = signedBeaconBlockDeneb;
    this.signedBlobSidecars = signedBlobSidecars;
  }

  public SignedBlockContents(
      final tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents
          signedBlockContents) {
    this.signedBeaconBlockDeneb =
        new SignedBeaconBlockDeneb(signedBlockContents.getSignedBeaconBlock().orElseThrow());
    this.signedBlobSidecars =
        signedBlockContents.getSignedBlobSidecars().orElseThrow().stream()
            .map(SignedBlobSidecar::new)
            .collect(Collectors.toList());
  }

  public SignedBlockContents() {}

  public static BlockContents create(
      final tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents
          blockContents) {
    return new BlockContents(blockContents);
  }

  public tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents
      asInternalSignedBlockContents(
          final SignedBlockContentsSchema signedBlockContentsSchema,
          final SignedBlobSidecarSchema signedBlobSidecarSchema,
          final Spec spec) {
    return signedBlockContentsSchema.create(
        signedBeaconBlockDeneb.asInternalSignedBeaconBlock(spec),
        signedBlobSidecars.stream()
            .map(
                signedBlobSidecar ->
                    signedBlobSidecar.asInternalSignedBlobSidecar(signedBlobSidecarSchema))
            .collect(Collectors.toList()));
  }

  public static Predicate<BlockContainer> isInstance =
      signedBlockContent -> signedBlockContent instanceof SignedBlockContents;
}

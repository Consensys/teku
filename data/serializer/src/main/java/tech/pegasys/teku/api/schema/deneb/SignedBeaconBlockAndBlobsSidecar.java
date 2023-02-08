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

package tech.pegasys.teku.api.schema.deneb;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockAndBlobsSidecarSchema;

public class SignedBeaconBlockAndBlobsSidecar {

  @JsonProperty("beacon_block")
  private final SignedBeaconBlockDeneb signedBeaconBlock;

  @JsonProperty("blobs_sidecar")
  private final BlobsSidecar blobsSidecar;

  public SignedBeaconBlockAndBlobsSidecar(
      @JsonProperty("beacon_block") final SignedBeaconBlockDeneb signedBeaconBlock,
      @JsonProperty("blobs_sidecar") final BlobsSidecar blobsSidecar) {
    this.signedBeaconBlock = signedBeaconBlock;
    this.blobsSidecar = blobsSidecar;
  }

  public SignedBeaconBlockAndBlobsSidecar(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
              .SignedBeaconBlockAndBlobsSidecar
          signedBeaconBlockAndBlobsSidecar) {
    this.signedBeaconBlock =
        (SignedBeaconBlockDeneb)
            SignedBeaconBlock.create(signedBeaconBlockAndBlobsSidecar.getSignedBeaconBlock());
    this.blobsSidecar = BlobsSidecar.create(signedBeaconBlockAndBlobsSidecar.getBlobsSidecar());
  }

  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
          .SignedBeaconBlockAndBlobsSidecar
      asInternalSignedBeaconBlockAndBlobsSidecar(
          final SignedBeaconBlockAndBlobsSidecarSchema schema, final Spec spec) {
    return schema.create(
        signedBeaconBlock.asInternalSignedBeaconBlock(spec),
        blobsSidecar.asInternalBlobSidecar(schema.getBlobsSidecarSchema()));
  }
}

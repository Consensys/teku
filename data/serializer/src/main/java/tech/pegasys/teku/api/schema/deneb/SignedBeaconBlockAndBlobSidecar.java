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
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockAndBlobSidecarSchema;

public class SignedBeaconBlockAndBlobSidecar {

  @JsonProperty("beacon_block")
  private final SignedBeaconBlockDeneb signedBeaconBlock;

  @JsonProperty("blob_sidecar")
  private final BlobSidecar blobSidecar;

  public SignedBeaconBlockAndBlobSidecar(
      @JsonProperty("beacon_block") final SignedBeaconBlockDeneb signedBeaconBlock,
      @JsonProperty("blob_sidecar") final BlobSidecar blobSidecar) {
    this.signedBeaconBlock = signedBeaconBlock;
    this.blobSidecar = blobSidecar;
  }

  public SignedBeaconBlockAndBlobSidecar(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
              .SignedBeaconBlockAndBlobSidecar
          signedBeaconBlockAndBlobSidecar) {
    this.signedBeaconBlock =
        (SignedBeaconBlockDeneb)
            SignedBeaconBlock.create(signedBeaconBlockAndBlobSidecar.getSignedBeaconBlock());
    this.blobSidecar = BlobSidecar.create(signedBeaconBlockAndBlobSidecar.getBlobSidecar());
  }

  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
          .SignedBeaconBlockAndBlobSidecar
      asInternalSignedBeaconBlockAndBlobSidecar(
          final SignedBeaconBlockAndBlobSidecarSchema schema, final Spec spec) {
    return schema.create(
        signedBeaconBlock.asInternalSignedBeaconBlock(spec),
        blobSidecar.asInternalBlobSidecar(schema.getBlobSidecarSchema()));
  }
}

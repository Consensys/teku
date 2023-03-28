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

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlindedBlobSidecarSchema;

public class SignedBlindedBlobSidecar {

  @JsonProperty("message")
  private final BlindedBlobSidecar blindedBlobSidecar;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public final BLSSignature signature;

  public SignedBlindedBlobSidecar(
      @JsonProperty("message") final BlindedBlobSidecar blindedBlobSidecar,
      @JsonProperty("signature") final BLSSignature signature) {
    this.blindedBlobSidecar = blindedBlobSidecar;
    this.signature = signature;
  }

  public SignedBlindedBlobSidecar(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
              .SignedBlindedBlobSidecar
          signedBlindedBlobSidecar) {
    this.blindedBlobSidecar =
        BlindedBlobSidecar.create(signedBlindedBlobSidecar.getBlindedBlobSidecar());
    this.signature = new BLSSignature(signedBlindedBlobSidecar.getSignature());
  }

  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
          .SignedBlindedBlobSidecar
      asInternalSignedBlindedBlobSidecar(final SignedBlindedBlobSidecarSchema schema) {
    return schema.create(
        blindedBlobSidecar.asInternalBlindedBlobSidecar(schema.getBlindedBlobSidecarSchema()),
        signature.asInternalBLSSignature());
  }
}

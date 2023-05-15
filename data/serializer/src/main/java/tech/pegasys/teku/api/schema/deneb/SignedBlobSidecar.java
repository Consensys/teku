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

import static tech.pegasys.teku.api.schema.BLSSignature.BLS_SIGNATURE_TYPE;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;
import static tech.pegasys.teku.api.schema.deneb.BlobSidecar.BLOB_SIDECAR_TYPE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecarSchema;

public class SignedBlobSidecar {

  @JsonProperty("message")
  private BlobSidecar blobSidecar;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public BLSSignature signature;

  public static final DeserializableTypeDefinition<SignedBlobSidecar> SIGNED_BLOB_SIDECAR_TYPE =
      DeserializableTypeDefinition.object(SignedBlobSidecar.class)
          .name("SignedBeaconBlock")
          .initializer(SignedBlobSidecar::new)
          .withField(
              "message",
              BLOB_SIDECAR_TYPE,
              SignedBlobSidecar::getBlobSidecar,
              SignedBlobSidecar::setBlobSidecar)
          .withField(
              "signature",
              BLS_SIGNATURE_TYPE,
              SignedBlobSidecar::getSignature,
              SignedBlobSidecar::setSignature)
          .build();

  public SignedBlobSidecar(
      @JsonProperty("message") final BlobSidecar blobSidecar,
      @JsonProperty("signature") final BLSSignature signature) {
    this.blobSidecar = blobSidecar;
    this.signature = signature;
  }

  public SignedBlobSidecar(
      final tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar
          signedBlobSidecar) {
    this.blobSidecar = BlobSidecar.create(signedBlobSidecar.getBlobSidecar());
    this.signature = new BLSSignature(signedBlobSidecar.getSignature());
  }

  public SignedBlobSidecar() {}

  public BlobSidecar getBlobSidecar() {
    return blobSidecar;
  }

  public void setBlobSidecar(BlobSidecar blobSidecar) {
    this.blobSidecar = blobSidecar;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public void setSignature(BLSSignature signature) {
    this.signature = signature;
  }

  public tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar
      asInternalSignedBlobSidecar(final SignedBlobSidecarSchema schema) {
    return schema.create(
        blobSidecar.asInternalBlobSidecar(schema.getBlobSidecarSchema()),
        signature.asInternalBLSSignature());
  }
}

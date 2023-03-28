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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlindedBlobSidecarsSchema;

public class SignedBlindedBlobSidecars {

  @ArraySchema(schema = @Schema(type = "string", format = "byte"))
  public final List<SignedBlindedBlobSidecar> signedBlindedBlobSidecars;

  public SignedBlindedBlobSidecars(
      @JsonProperty("signed_blinded_blob_sidecars")
          final List<SignedBlindedBlobSidecar> signedBlindedBlobSidecars) {
    this.signedBlindedBlobSidecars = signedBlindedBlobSidecars;
  }

  public SignedBlindedBlobSidecars(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
              .SignedBlindedBlobSidecars
          signedBlindedBlobSidecars) {
    this.signedBlindedBlobSidecars =
        signedBlindedBlobSidecars.getSignedBlindedBlobSidecars().stream()
            .map(SignedBlindedBlobSidecar::new)
            .collect(Collectors.toList());
  }

  public static SignedBlindedBlobSidecars create(
      final List<
              tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
                  .SignedBlindedBlobSidecar>
          signedBlindedBlobSidecars) {
    return new SignedBlindedBlobSidecars(
        signedBlindedBlobSidecars.stream()
            .map(SignedBlindedBlobSidecar::new)
            .collect(Collectors.toList()));
  }

  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
          .SignedBlindedBlobSidecars
      asInternalSignedBlindedBlobSidecars(final SignedBlindedBlobSidecarsSchema schema) {
    return schema.create(
        signedBlindedBlobSidecars.stream()
            .map(
                signedBlindedBlobSidecar ->
                    signedBlindedBlobSidecar.asInternalSignedBlindedBlobSidecar(
                        schema.getSignedBlindedBlobSidecarSchema()))
            .collect(Collectors.toList()));
  }
}

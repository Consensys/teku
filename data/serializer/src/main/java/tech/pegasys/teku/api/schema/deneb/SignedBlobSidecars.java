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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlobSidecarsSchema;

public class SignedBlobSidecars {

  @ArraySchema(schema = @Schema(type = "string", format = "byte"))
  public final List<SignedBlobSidecar> signedBlobSidecars;

  public SignedBlobSidecars(
      @JsonProperty("signed_blob_sidecars") final List<SignedBlobSidecar> signedBlobSidecars) {
    this.signedBlobSidecars = signedBlobSidecars;
  }

  public SignedBlobSidecars(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlobSidecars
          signedBlobSidecars) {
    this.signedBlobSidecars =
        signedBlobSidecars.getBlobSidecars().stream()
            .map(SignedBlobSidecar::new)
            .collect(Collectors.toList());
  }

  public static SignedBlobSidecars create(
      final List<
              tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
                  .SignedBlobSidecar>
          signedBlobSidecars) {
    return new SignedBlobSidecars(
        signedBlobSidecars.stream().map(SignedBlobSidecar::new).collect(Collectors.toList()));
  }

  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlobSidecars
      asInternalSignedBlobSidecars(final SignedBlobSidecarsSchema schema) {
    return schema.create(
        signedBlobSidecars.stream()
            .map(
                signedBlobSidecar ->
                    signedBlobSidecar.asInternalSignedBlobSidecar(
                        schema.getSignedBlobSidecarSchema()))
            .collect(Collectors.toList()));
  }
}

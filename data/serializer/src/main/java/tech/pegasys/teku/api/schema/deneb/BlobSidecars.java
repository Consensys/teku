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
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecarsSchema;

public class BlobSidecars {

  @ArraySchema(schema = @Schema(type = "string", format = "byte"))
  public final List<BlobSidecar> blobSidecars;

  public BlobSidecars(@JsonProperty("blob_sidecars") final List<BlobSidecar> blobSidecars) {
    this.blobSidecars = blobSidecars;
  }

  public BlobSidecars(
      final tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecars
          blobSidecars) {
    this.blobSidecars =
        blobSidecars.getBlobSidecars().stream().map(BlobSidecar::new).collect(Collectors.toList());
  }

  public static BlobSidecars create(
      final tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecars
          blobSidecars) {
    return new BlobSidecars(blobSidecars);
  }

  public tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecars
      asInternalBlobSidecars(final BlobSidecarsSchema blobSidecarsSchema) {
    return blobSidecarsSchema.create(
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    blobSidecar.asInternalBlobSidecar(blobSidecarsSchema.getBlobSidecarSchema()))
            .collect(Collectors.toList()));
  }
}

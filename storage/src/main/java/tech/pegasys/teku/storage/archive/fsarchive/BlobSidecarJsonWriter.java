/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.storage.archive.fsarchive;

import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class BlobSidecarJsonWriter {

  private final SerializableTypeDefinition<List<BlobSidecar>> blobSidecarType;

  public BlobSidecarJsonWriter(final Spec spec) {
    SchemaDefinitionCache schemaCache = new SchemaDefinitionCache(spec);
    this.blobSidecarType =
        listOf(
            SchemaDefinitionsDeneb.required(schemaCache.getSchemaDefinition(SpecMilestone.DENEB))
                .getBlobSidecarSchema()
                .getJsonTypeDefinition());
  }

  public void writeSlotBlobSidecars(final OutputStream out, final List<BlobSidecar> blobSidecar)
      throws IOException {
    Objects.requireNonNull(out);
    Objects.requireNonNull(blobSidecar);

    JsonUtil.serializeToBytes(blobSidecar, blobSidecarType, out);
  }
}

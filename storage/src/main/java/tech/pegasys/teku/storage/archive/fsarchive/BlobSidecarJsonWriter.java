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

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

public class BlobSidecarJsonWriter {

  public void writeSlotBlobSidecars(final OutputStream out, final List<BlobSidecar> blobSidecars)
      throws IOException {
    Objects.requireNonNull(out);
    Objects.requireNonNull(blobSidecars);

    // Technically not possible as pruner prunes sidecars and not slots.
    if (blobSidecars.isEmpty()) {
      out.write("[]".getBytes(UTF_8));
      return;
    }

    final SerializableTypeDefinition<List<BlobSidecar>> type =
        listOf(blobSidecars.getFirst().getSchema().getJsonTypeDefinition());
    JsonUtil.serializeToBytes(blobSidecars, type, out);
  }
}

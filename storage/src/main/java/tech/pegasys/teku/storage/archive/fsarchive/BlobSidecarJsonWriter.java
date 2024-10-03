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

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.io.IOException;
import java.io.OutputStream;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

public class BlobSidecarJsonWriter {

  public static final StringValueTypeDefinition<KZGCommitment> KZG_COMMITMENT_TYPE =
      DeserializableTypeDefinition.string(KZGCommitment.class)
          .formatter(KZGCommitment::toHexString)
          .parser(KZGCommitment::fromHexString)
          .example(
              "0xb09ce4964278eff81a976fbc552488cb84fc4a102f004c87"
                  + "179cb912f49904d1e785ecaf5d184522a58e9035875440ef")
          .description("KZG Commitment")
          .format("byte")
          .build();

  public static final SerializableTypeDefinition<BlobSidecar> BLOB_SIDECAR_TYPE =
      SerializableTypeDefinition.object(BlobSidecar.class)
          .name("BlobSidecar")
          .withField("index", UINT64_TYPE, BlobSidecar::getIndex)
          // .withField( "blob", CoreTypes, BlobSidecar::getBlob)
          .withField("block_root", BYTES32_TYPE, BlobSidecar::getBlockRoot)
          .withField("slot", UINT64_TYPE, BlobSidecar::getSlot)
          .withField("kzg_commitment", KZG_COMMITMENT_TYPE, BlobSidecar::getKZGCommitment)
          .build();

  public BlobSidecarJsonWriter() {}

  public void writeBlobSidecar(final OutputStream out, final BlobSidecar blobSidecar)
      throws IOException {

    String output = JsonUtil.prettySerialize(blobSidecar, BLOB_SIDECAR_TYPE);
    System.out.println(output);
  }
}

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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SignedBlobSidecarTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimal(SpecMilestone.DENEB));
  final SchemaDefinitions schemaDefinitions =
      dataStructureUtil.getSpec().getGenesisSchemaDefinitions();
  private final SignedBlobSidecarSchema signedBlobSidecarSchema =
      schemaDefinitions.toVersionDeneb().orElseThrow().getSignedBlobSidecarSchema();
  private final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();

  private final BLSSignature signature = dataStructureUtil.randomSignature();

  @Test
  public void objectEquality() {
    final SignedBlobSidecar signedBlobSidecar1 =
        signedBlobSidecarSchema.create(blobSidecar, signature);
    final SignedBlobSidecar signedBlobSidecar2 =
        signedBlobSidecarSchema.create(blobSidecar, signature);

    assertThat(signedBlobSidecar1).isEqualTo(signedBlobSidecar2);
  }

  @Test
  public void objectAccessorMethods() {
    final SignedBlobSidecar signedBlobSidecar =
        signedBlobSidecarSchema.create(blobSidecar, signature);

    assertThat(signedBlobSidecar.getBlobSidecar()).isEqualTo(blobSidecar);
    assertThat(signedBlobSidecar.getSignature()).isEqualTo(signature);
  }

  @Test
  public void roundTripSSZ() {
    final SignedBlobSidecar signedBlobSidecar =
        signedBlobSidecarSchema.create(blobSidecar, signature);

    final Bytes sszSignedBlobSidecarBytes = signedBlobSidecar.sszSerialize();
    final SignedBlobSidecar deserializedObject =
        signedBlobSidecarSchema.sszDeserialize(sszSignedBlobSidecarBytes);

    assertThat(signedBlobSidecar).isEqualTo(deserializedObject);
  }
}

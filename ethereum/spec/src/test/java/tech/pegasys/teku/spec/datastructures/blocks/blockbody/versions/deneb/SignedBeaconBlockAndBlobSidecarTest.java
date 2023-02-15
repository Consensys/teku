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
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SignedBeaconBlockAndBlobSidecarTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimal(SpecMilestone.DENEB));
  final SchemaDefinitions schemaDefinitions =
      dataStructureUtil.getSpec().getGenesisSchemaDefinitions();
  private final SignedBeaconBlockAndBlobSidecarSchema signedBeaconBlockAndBlobSidecarSchema =
      schemaDefinitions.toVersionDeneb().orElseThrow().getSignedBeaconBlockAndBlobSidecarSchema();

  private final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock();
  private final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();

  @Test
  public void objectEquality() {
    final SignedBeaconBlockAndBlobSidecar signedBeaconBlockAndBlobSidecar1 =
        signedBeaconBlockAndBlobSidecarSchema.create(signedBeaconBlock, blobSidecar);
    final SignedBeaconBlockAndBlobSidecar signedBeaconBlockAndBlobSidecar2 =
        signedBeaconBlockAndBlobSidecarSchema.create(signedBeaconBlock, blobSidecar);

    assertThat(signedBeaconBlockAndBlobSidecar1).isEqualTo(signedBeaconBlockAndBlobSidecar2);
  }

  @Test
  public void objectAccessorMethods() {
    final SignedBeaconBlockAndBlobSidecar signedBeaconBlockAndBlobSidecar =
        signedBeaconBlockAndBlobSidecarSchema.create(signedBeaconBlock, blobSidecar);

    assertThat(signedBeaconBlockAndBlobSidecar.getSignedBeaconBlock()).isEqualTo(signedBeaconBlock);
    assertThat(signedBeaconBlockAndBlobSidecar.getBlobSidecar()).isEqualTo(blobSidecar);
  }

  @Test
  public void roundTripSSZ() {
    final SignedBeaconBlockAndBlobSidecar signedBeaconBlockAndBlobSidecar =
        signedBeaconBlockAndBlobSidecarSchema.create(signedBeaconBlock, blobSidecar);

    final Bytes sszSignedBeaconBlockAndBlobsSidecarBytes =
        signedBeaconBlockAndBlobSidecar.sszSerialize();
    final SignedBeaconBlockAndBlobSidecar deserializedObject =
        signedBeaconBlockAndBlobSidecarSchema.sszDeserialize(
            sszSignedBeaconBlockAndBlobsSidecarBytes);

    assertThat(signedBeaconBlockAndBlobSidecar).isEqualTo(deserializedObject);
  }
}

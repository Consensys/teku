/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SignedBeaconBlockAndBlobsSidecarTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimal(SpecMilestone.EIP4844));
  final SchemaDefinitions schemaDefinitions =
      dataStructureUtil.getSpec().getGenesisSchemaDefinitions();
  private final SignedBeaconBlockAndBlobsSidecarSchema signedBeaconBlockAndBlobsSidecarSchema =
      schemaDefinitions
          .toVersionEip4844()
          .orElseThrow()
          .getSignedBeaconBlockAndBlobsSidecarSchema();

  private final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock();
  private final BlobsSidecar blobsSidecar = dataStructureUtil.randomBlobsSidecar();

  @Test
  public void objectEquality() {
    final SignedBeaconBlockAndBlobsSidecar signedBeaconBlockAndBlobsSidecar1 =
        signedBeaconBlockAndBlobsSidecarSchema.create(signedBeaconBlock, blobsSidecar);
    final SignedBeaconBlockAndBlobsSidecar signedBeaconBlockAndBlobsSidecar2 =
        signedBeaconBlockAndBlobsSidecarSchema.create(signedBeaconBlock, blobsSidecar);

    assertThat(signedBeaconBlockAndBlobsSidecar1).isEqualTo(signedBeaconBlockAndBlobsSidecar2);
  }

  @Test
  public void objectAccessorMethods() {
    final SignedBeaconBlockAndBlobsSidecar signedBeaconBlockAndBlobsSidecar =
        signedBeaconBlockAndBlobsSidecarSchema.create(signedBeaconBlock, blobsSidecar);

    assertThat(signedBeaconBlockAndBlobsSidecar.getSignedBeaconBlock())
        .isEqualTo(signedBeaconBlock);
    assertThat(signedBeaconBlockAndBlobsSidecar.getBlobsSidecar()).isEqualTo(blobsSidecar);
  }

  @Test
  public void roundTripSSZ() {
    final SignedBeaconBlockAndBlobsSidecar signedBeaconBlockAndBlobsSidecar =
        signedBeaconBlockAndBlobsSidecarSchema.create(signedBeaconBlock, blobsSidecar);

    final Bytes sszSignedBeaconBlockAndBlobsSidecarBytes =
        signedBeaconBlockAndBlobsSidecar.sszSerialize();
    final SignedBeaconBlockAndBlobsSidecar deserializedObject =
        signedBeaconBlockAndBlobsSidecarSchema.sszDeserialize(
            sszSignedBeaconBlockAndBlobsSidecarBytes);

    assertThat(signedBeaconBlockAndBlobsSidecar).isEqualTo(deserializedObject);
  }
}

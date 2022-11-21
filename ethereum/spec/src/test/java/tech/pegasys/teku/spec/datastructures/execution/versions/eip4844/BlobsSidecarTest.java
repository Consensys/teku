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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip4844;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Streams;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlobsSidecarTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimal(SpecMilestone.EIP4844));
  final SchemaDefinitions schemaDefinitions =
      dataStructureUtil.getSpec().getGenesisSchemaDefinitions();
  private final BlobsSidecarSchema blobsSidecarSchema =
      schemaDefinitions.toVersionEip4844().orElseThrow().getBlobsSidecarSchema();

  private final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
  private final UInt64 beaconBlockSlot = dataStructureUtil.randomUInt64();
  private final List<Bytes> blobs =
      IntStream.range(0, (int) blobsSidecarSchema.getBlobsSchema().getMaxLength())
          .mapToObj(
              __ -> dataStructureUtil.randomBytes(blobsSidecarSchema.getBlobSchema().getLength()))
          .collect(toList());
  private final Bytes48 kzgAggregatedProof = dataStructureUtil.randomBytes48();

  @Test
  public void objectEquality() {
    final BlobsSidecar blobsSidecar1 =
        blobsSidecarSchema.create(beaconBlockRoot, beaconBlockSlot, blobs, kzgAggregatedProof);
    final BlobsSidecar blobsSidecar2 =
        blobsSidecarSchema.create(beaconBlockRoot, beaconBlockSlot, blobs, kzgAggregatedProof);

    assertThat(blobsSidecar1).isEqualTo(blobsSidecar2);
  }

  @Test
  public void objectAccessorMethods() {
    final BlobsSidecar blobsSidecar =
        blobsSidecarSchema.create(beaconBlockRoot, beaconBlockSlot, blobs, kzgAggregatedProof);

    assertThat(blobsSidecar.getBeaconBlockRoot()).isEqualTo(beaconBlockRoot);
    assertThat(blobsSidecar.getBeaconBlockSlot()).isEqualTo(beaconBlockSlot);
    assertThat(blobsSidecar.getBlobs().stream().map(Blob::getBytes).collect(toList()))
        .isEqualTo(blobs);
    assertThat(blobsSidecar.getKZGAggregatedProof().getBytesCompressed())
        .isEqualTo(kzgAggregatedProof);
  }

  @Test
  public void moreBlobsThanAllowed() {
    assertThatThrownBy(
            () ->
                blobsSidecarSchema.create(
                    beaconBlockRoot,
                    beaconBlockSlot,
                    Streams.concat(blobs.stream(), Stream.of(blobs.get(0))).collect(toList()),
                    kzgAggregatedProof))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void roundTripSSZ() {
    final BlobsSidecar blobsSidecar =
        blobsSidecarSchema.create(beaconBlockRoot, beaconBlockSlot, blobs, kzgAggregatedProof);

    final Bytes sszBlobsSidecarBytes = blobsSidecar.sszSerialize();
    final BlobsSidecar deserializedObject = blobsSidecarSchema.sszDeserialize(sszBlobsSidecarBytes);

    assertThat(blobsSidecar).isEqualTo(deserializedObject);
  }
}

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

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class BlobsSidecar
    extends Container4<BlobsSidecar, SszBytes32, SszUInt64, SszList<Blob>, SszKZGProof> {

  BlobsSidecar(final BlobsSidecarSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BlobsSidecar(
      final BlobsSidecarSchema schema,
      final Bytes32 beaconBlockRoot,
      final UInt64 beaconBlockSlot,
      final List<Blob> blobs,
      final KZGProof kzgAggregatedProof) {
    super(
        schema,
        SszBytes32.of(beaconBlockRoot),
        SszUInt64.of(beaconBlockSlot),
        schema.getBlobsSchema().createFromElements(blobs),
        new SszKZGProof(kzgAggregatedProof));
  }

  public Bytes32 getBeaconBlockRoot() {
    return getField0().get();
  }

  public UInt64 getBeaconBlockSlot() {
    return getField1().get();
  }

  public List<Blob> getBlobs() {
    return getField2().asList();
  }

  public KZGProof getKZGAggregatedProof() {
    return getField3().getKZGProof();
  }
}

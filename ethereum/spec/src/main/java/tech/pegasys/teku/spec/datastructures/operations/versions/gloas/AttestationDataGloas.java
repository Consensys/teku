/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.operations.versions.gloas;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class AttestationDataGloas
    extends Container5<
        AttestationDataGloas, SszUInt64, SszUInt64, SszBytes32, Checkpoint, Checkpoint>
    implements AttestationData {
  AttestationDataGloas(final AttestationDataGloasSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  AttestationDataGloas(
      final AttestationDataGloasSchema schema,
      final UInt64 slot,
      final UInt64 index,
      final Bytes32 beaconBlockRoot,
      final Checkpoint source,
      final Checkpoint target) {
    super(
        schema,
        SszUInt64.of(slot),
        SszUInt64.of(index),
        SszBytes32.of(beaconBlockRoot),
        source,
        target);
  }

  @Override
  public UInt64 getSlot() {
    return getField0().get();
  }

  @Override
  public Optional<UInt64> getIndex() {
    return Optional.empty();
  }

  @Override
  public Optional<UInt64> getPayloadStatus() {
    return Optional.of(getField1().get());
  }

  @Override
  public Bytes32 getBeaconBlockRoot() {
    return getField2().get();
  }

  @Override
  public Checkpoint getSource() {
    return getField3();
  }

  @Override
  public Checkpoint getTarget() {
    return getField4();
  }

  @Override
  public AttestationDataGloasSchema getSchema() {
    return (AttestationDataGloasSchema) super.getSchema();
  }
}

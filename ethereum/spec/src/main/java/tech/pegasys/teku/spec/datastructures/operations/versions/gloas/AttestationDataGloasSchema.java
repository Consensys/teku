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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationDataSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class AttestationDataGloasSchema
    extends ContainerSchema5<
        AttestationDataGloas, SszUInt64, SszUInt64, SszBytes32, Checkpoint, Checkpoint>
    implements AttestationDataSchema<AttestationDataGloas> {

  public AttestationDataGloasSchema() {
    super(
        "AttestationData",
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("source", Checkpoint.SSZ_SCHEMA),
        namedSchema("target", Checkpoint.SSZ_SCHEMA));
  }

  @Override
  public AttestationDataGloas createFromBackingNode(final TreeNode node) {
    return new AttestationDataGloas(this, node);
  }

  @Override
  public AttestationData create(
      final UInt64 slot,
      final UInt64 index,
      final Bytes32 beaconBlockRoot,
      final Checkpoint source,
      final Checkpoint target) {
    return new AttestationDataGloas(this, slot, index, beaconBlockRoot, source, target);
  }
}

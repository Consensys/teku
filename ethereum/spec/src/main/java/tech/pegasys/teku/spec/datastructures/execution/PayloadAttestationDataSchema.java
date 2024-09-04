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

package tech.pegasys.teku.spec.datastructures.execution;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PayloadAttestationDataSchema
    extends ContainerSchema3<PayloadAttestationData, SszBytes32, SszUInt64, SszByte> {

  public PayloadAttestationDataSchema() {
    super(
        "PayloadAttestationData",
        namedSchema("beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("payload_status", SszPrimitiveSchemas.BYTE_SCHEMA));
  }

  public PayloadAttestationData create(
      final Bytes32 beaconBlockRoot, final UInt64 slot, final Byte payloadStatus) {
    return new PayloadAttestationData(this, beaconBlockRoot, slot, payloadStatus);
  }

  @Override
  public PayloadAttestationData createFromBackingNode(final TreeNode node) {
    return new PayloadAttestationData(this, node);
  }
}

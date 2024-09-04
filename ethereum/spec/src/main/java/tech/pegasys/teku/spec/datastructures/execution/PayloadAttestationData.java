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
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PayloadAttestationData
    extends Container3<PayloadAttestationData, SszBytes32, SszUInt64, SszByte> {

  PayloadAttestationData(
      final PayloadAttestationDataSchema schema,
      final Bytes32 beaconBlockRoot,
      final UInt64 slot,
      final Byte payloadStatus) {
    super(schema, SszBytes32.of(beaconBlockRoot), SszUInt64.of(slot), SszByte.of(payloadStatus));
  }

  PayloadAttestationData(final PayloadAttestationDataSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public static final PayloadAttestationDataSchema SSZ_SCHEMA = new PayloadAttestationDataSchema();

  public Bytes32 getBeaconBlockRoot() {
    return getField0().get();
  }

  public UInt64 getSlot() {
    return getField1().get();
  }

  public Byte getPayloadStatus() {
    return getField2().get();
  }

  @Override
  public PayloadAttestationDataSchema getSchema() {
    return (PayloadAttestationDataSchema) super.getSchema();
  }
}

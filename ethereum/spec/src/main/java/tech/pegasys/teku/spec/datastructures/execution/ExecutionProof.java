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

package tech.pegasys.teku.spec.datastructures.execution;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteListImpl;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

import java.util.List;

import static java.lang.Long.toHexString;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema.SSZ_SCHEMA;

public class ExecutionProof
    extends Container5<ExecutionProof, SszBytes32, SszBytes32, SszUInt64, SszUInt64, SszByteList> {

  public ExecutionProof(final ExecutionProofSchema schema, final TreeNode node) {
    super(schema, node);
  }

  public static ExecutionProof create(final UInt64 slot) {
      return SSZ_SCHEMA.create(
              SszBytes32.of(Bytes32.fromHexString(toHexString(slot.longValue()))),
              SszBytes32.of(Bytes32.fromHexString(toHexString(slot.longValue()))),
              SszUInt64.of(UInt64.ONE),
              SszUInt64.ZERO,
              SSZ_SCHEMA.getProofDataSchema().fromBytes(Bytes32.ZERO));
  }

  public static final ExecutionProof DUMMY_PROOF = SSZ_SCHEMA.create(
      SszBytes32.of(Bytes32.ZERO),
      SszBytes32.of(Bytes32.ZERO),
      SszUInt64.of(UInt64.ONE),
      SszUInt64.ZERO,
          SSZ_SCHEMA.getProofDataSchema().fromBytes(Bytes32.ZERO));

  public ExecutionProof(
      final ExecutionProofSchema schema,
      final SszBytes32 blockRoot,
      final SszBytes32 blockHash,
      final SszUInt64 subnetId,
      final SszUInt64 version,
      final SszByteList proofData) {
    super(schema, blockRoot, blockHash, subnetId, version, proofData);
  }

  public SszBytes32 getBlockRoot() {
    return getField0();
  }

    public SszBytes32 getBlockHash() {
        return getField1();
    }

  public SszUInt64 getSubnetId() {
    return getField2();
  }

  public SszUInt64 getVersion() {
    return getField3();
  }

  public SszByteList getProofData() {
    return getField4();
  }
}

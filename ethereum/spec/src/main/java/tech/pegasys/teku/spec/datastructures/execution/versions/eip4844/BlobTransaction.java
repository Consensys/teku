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
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszUnion;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container11;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class BlobTransaction
    extends Container11<
        BlobTransaction,
        SszUInt256,
        SszUInt64,
        SszUInt256,
        SszUInt256,
        SszUInt64,
        SszUnion,
        SszUInt256,
        SszByteList,
        SszList<AccessTuple>,
        SszUInt256,
        SszList<SszBytes32>> {

  BlobTransaction(final BlobTransactionSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BlobTransaction(
      final BlobTransactionSchema schema,
      final SszUInt256 chainId,
      final SszUInt64 nonce,
      final SszUInt256 maxPriorityFeePerGas,
      final SszUInt256 maxFeePerGas,
      final SszUInt64 gas,
      final SszUnion to,
      final SszUInt256 value,
      final SszByteList data,
      final SszList<AccessTuple> accessList,
      final SszUInt256 maxFeePerDataGas,
      final SszList<SszBytes32> blobVersionedHashes) {
    super(
        schema,
        chainId,
        nonce,
        maxPriorityFeePerGas,
        maxFeePerGas,
        gas,
        to,
        value,
        data,
        accessList,
        maxFeePerDataGas,
        blobVersionedHashes);
  }

  public static final BlobTransactionSchema SSZ_SCHEMA = new BlobTransactionSchema();

  public List<Bytes32> getBlobVersionedHashes() {
    return getField10().stream().map(AbstractSszPrimitive::get).collect(Collectors.toList());
  }
}

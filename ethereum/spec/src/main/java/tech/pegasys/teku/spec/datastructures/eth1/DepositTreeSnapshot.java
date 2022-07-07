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

package tech.pegasys.teku.spec.datastructures.eth1;

import static tech.pegasys.teku.spec.datastructures.eth1.DepositTreeSnapshotSchema.BYTES32_LIST_SCHEMA;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32List;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DepositTreeSnapshot
    extends Container3<DepositTreeSnapshot, SszBytes32List, SszUInt64, SszBytes32> {

  public static final DepositTreeSnapshotSchema SSZ_SCHEMA = new DepositTreeSnapshotSchema();

  DepositTreeSnapshot(final DepositTreeSnapshotSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  DepositTreeSnapshot(
      final DepositTreeSnapshotSchema type,
      final List<Bytes32> finalized,
      final UInt64 deposits,
      final Bytes32 executionBlockHash) {
    this(
        type,
        BYTES32_LIST_SCHEMA.createFromElements(
            finalized.stream().map(SszBytes32::of).collect(Collectors.toList())),
        SszUInt64.of(deposits),
        SszBytes32.of(executionBlockHash));
  }

  DepositTreeSnapshot(
      final DepositTreeSnapshotSchema type,
      final SszBytes32List finalized,
      final SszUInt64 deposits,
      final SszBytes32 executionBlockHash) {
    super(type, finalized, deposits, executionBlockHash);
  }

  @Override
  public DepositTreeSnapshotSchema getSchema() {
    return (DepositTreeSnapshotSchema) super.getSchema();
  }

  public List<Bytes32> getFinalized() {
    return getField0().asListUnboxed();
  }

  public UInt64 getDeposits() {
    return getField1().get();
  }

  public Bytes32 getExecutionBlockHash() {
    return getField2().get();
  }
}

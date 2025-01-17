/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;

public class DataColumnIdentifier extends Container2<DataColumnIdentifier, SszBytes32, SszUInt64> {

  public static class DataColumnIdentifierSchema
      extends ContainerSchema2<DataColumnIdentifier, SszBytes32, SszUInt64> {

    private DataColumnIdentifierSchema() {
      super(
          "DataColumnIdentifier",
          namedSchema("block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("index", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public DataColumnIdentifier createFromBackingNode(final TreeNode node) {
      return new DataColumnIdentifier(node);
    }
  }

  public static final DataColumnIdentifierSchema SSZ_SCHEMA = new DataColumnIdentifierSchema();

  public static DataColumnIdentifier createFromSidecar(final DataColumnSidecar sidecar) {
    return new DataColumnIdentifier(sidecar.getBlockRoot(), sidecar.getIndex());
  }

  private DataColumnIdentifier(final TreeNode node) {
    super(SSZ_SCHEMA, node);
  }

  public DataColumnIdentifier(final Bytes32 root, final UInt64 index) {
    super(SSZ_SCHEMA, SszBytes32.of(root), SszUInt64.of(index));
  }

  public Bytes32 getBlockRoot() {
    return getField0().get();
  }

  public UInt64 getIndex() {
    return getField1().get();
  }
}

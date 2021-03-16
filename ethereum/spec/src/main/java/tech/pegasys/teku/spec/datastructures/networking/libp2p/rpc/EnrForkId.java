/*
 * Copyright 2020 ConsenSys AG.
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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema3;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes4;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.type.Bytes4;

public class EnrForkId extends Container3<EnrForkId, SszBytes4, SszBytes4, SszUInt64> {

  public static class EnrForkIdSchema
      extends ContainerSchema3<EnrForkId, SszBytes4, SszBytes4, SszUInt64> {

    public EnrForkIdSchema() {
      super(
          "EnrForkId",
          namedSchema("forkDigest", SszPrimitiveSchemas.BYTES4_SCHEMA),
          namedSchema("nextForkVersion", SszPrimitiveSchemas.BYTES4_SCHEMA),
          namedSchema("nextForkEpoch", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public EnrForkId createFromBackingNode(TreeNode node) {
      return new EnrForkId(this, node);
    }
  }

  public static final EnrForkIdSchema SSZ_SCHEMA = new EnrForkIdSchema();

  private EnrForkId(EnrForkIdSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public EnrForkId(
      final Bytes4 forkDigest, final Bytes4 nextForkVersion, final UInt64 nextForkEpoch) {
    super(
        SSZ_SCHEMA,
        SszBytes4.of(forkDigest),
        SszBytes4.of(nextForkVersion),
        SszUInt64.of(nextForkEpoch));
  }

  public Bytes4 getForkDigest() {
    return getField0().get();
  }

  public Bytes4 getNextForkVersion() {
    return getField1().get();
  }

  public UInt64 getNextForkEpoch() {
    return getField2().get();
  }
}

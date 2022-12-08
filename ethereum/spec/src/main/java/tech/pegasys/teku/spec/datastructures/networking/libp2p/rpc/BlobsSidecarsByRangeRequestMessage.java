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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlobsSidecarsByRangeRequestMessage
    extends Container2<BlobsSidecarsByRangeRequestMessage, SszUInt64, SszUInt64>
    implements RpcRequest {

  public static class BlobsSidecarsByRangeRequestMessageSchema
      extends ContainerSchema2<BlobsSidecarsByRangeRequestMessage, SszUInt64, SszUInt64> {

    public BlobsSidecarsByRangeRequestMessageSchema() {
      super(
          "BlobsSidecarsByRangeRequestMessage",
          namedSchema("startSlot", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("count", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public BlobsSidecarsByRangeRequestMessage createFromBackingNode(final TreeNode node) {
      return new BlobsSidecarsByRangeRequestMessage(this, node);
    }
  }

  public static final BlobsSidecarsByRangeRequestMessageSchema SSZ_SCHEMA =
      new BlobsSidecarsByRangeRequestMessageSchema();

  private BlobsSidecarsByRangeRequestMessage(
      final BlobsSidecarsByRangeRequestMessageSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BlobsSidecarsByRangeRequestMessage(final UInt64 startSlot, final UInt64 count) {
    super(SSZ_SCHEMA, SszUInt64.of(startSlot), SszUInt64.of(count));
  }

  public UInt64 getStartSlot() {
    return getField0().get();
  }

  public UInt64 getCount() {
    return getField1().get();
  }

  public UInt64 getMaxSlot() {
    return getStartSlot().plus(getCount().minus(1));
  }

  @Override
  public int getMaximumRequestChunks() {
    return Math.toIntExact(getCount().longValue());
  }
}

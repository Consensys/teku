/*
 * Copyright 2019 ConsenSys AG.
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
import tech.pegasys.teku.ssz.containers.Container3;
import tech.pegasys.teku.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.ssz.impl.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.tree.TreeNode;

public final class BeaconBlocksByRangeRequestMessage
    extends Container3<BeaconBlocksByRangeRequestMessage, SszUInt64, SszUInt64, SszUInt64>
    implements RpcRequest {

  public static class BeaconBlocksByRangeRequestMessageSchema
      extends ContainerSchema3<BeaconBlocksByRangeRequestMessage, SszUInt64, SszUInt64, SszUInt64> {

    public BeaconBlocksByRangeRequestMessageSchema() {
      super(
          "BeaconBlocksByRangeRequestMessage",
          namedSchema("startSlot", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("count", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("step", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public BeaconBlocksByRangeRequestMessage createFromBackingNode(TreeNode node) {
      return new BeaconBlocksByRangeRequestMessage(this, node);
    }
  }

  public static final BeaconBlocksByRangeRequestMessageSchema SSZ_SCHEMA =
      new BeaconBlocksByRangeRequestMessageSchema();

  private BeaconBlocksByRangeRequestMessage(
      BeaconBlocksByRangeRequestMessageSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public BeaconBlocksByRangeRequestMessage(
      final UInt64 startSlot, final UInt64 count, final UInt64 step) {
    super(SSZ_SCHEMA, SszUInt64.of(startSlot), SszUInt64.of(count), SszUInt64.of(step));
  }

  public UInt64 getStartSlot() {
    return getField0().get();
  }

  public UInt64 getCount() {
    return getField1().get();
  }

  public UInt64 getStep() {
    return getField2().get();
  }

  @Override
  public int getMaximumRequestChunks() {
    return Math.toIntExact(getCount().longValue());
  }
}

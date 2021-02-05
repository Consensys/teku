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

package tech.pegasys.teku.datastructures.networking.libp2p.rpc;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.containers.Container1;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema1;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public final class GoodbyeMessage extends Container1<GoodbyeMessage, SszUInt64>
    implements RpcRequest {

  public static class GoodbyeMessageSchema extends ContainerSchema1<GoodbyeMessage, SszUInt64> {

    public GoodbyeMessageSchema() {
      super("GoodbyeMessage", namedSchema("reason", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public GoodbyeMessage createFromBackingNode(TreeNode node) {
      return new GoodbyeMessage(this, node);
    }
  }

  public static final GoodbyeMessageSchema SSZ_SCHEMA = new GoodbyeMessageSchema();

  public static final UInt64 REASON_CLIENT_SHUT_DOWN = UInt64.valueOf(1);
  public static final UInt64 REASON_IRRELEVANT_NETWORK = UInt64.valueOf(2);
  public static final UInt64 REASON_FAULT_ERROR = UInt64.valueOf(3);
  public static final UInt64 MIN_CUSTOM_REASON_CODE = UInt64.valueOf(128);

  // Custom reasons
  public static final UInt64 REASON_UNABLE_TO_VERIFY_NETWORK = UInt64.valueOf(128);
  public static final UInt64 REASON_TOO_MANY_PEERS = UInt64.valueOf(129);
  public static final UInt64 REASON_RATE_LIMITING = UInt64.valueOf(130);

  private GoodbyeMessage(GoodbyeMessageSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public GoodbyeMessage(UInt64 reason) {
    super(SSZ_SCHEMA, new SszUInt64(reason));
    checkArgument(
        REASON_CLIENT_SHUT_DOWN.equals(reason)
            || REASON_FAULT_ERROR.equals(reason)
            || REASON_IRRELEVANT_NETWORK.equals(reason)
            || MIN_CUSTOM_REASON_CODE.compareTo(reason) <= 0,
        "Invalid reason code for Goodbye message");
  }

  public UInt64 getReason() {
    return getField0().get();
  }

  @Override
  public int getMaximumRequestChunks() {
    return 0;
  }
}

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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.containers.Container1;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema1;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

/** https://github.com/ethereum/eth2.0-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#metadata */
public class PingMessage extends Container1<PingMessage, SszUInt64> implements RpcRequest {

  static class PingMessageSchema extends ContainerSchema1<PingMessage, SszUInt64> {

    public PingMessageSchema() {
      super("PingMessage", namedSchema("seqNumber", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public PingMessage createFromBackingNode(TreeNode node) {
      return new PingMessage(this, node);
    }
  }

  public static final PingMessageSchema SSZ_SCHEMA = new PingMessageSchema();

  public PingMessage(PingMessageSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public PingMessage(UInt64 seqNumber) {
    super(SSZ_SCHEMA, new SszUInt64(seqNumber));
  }

  public UInt64 getSeqNumber() {
    return getField0().get();
  }

  @Override
  public int getMaximumRequestChunks() {
    return 1;
  }
}

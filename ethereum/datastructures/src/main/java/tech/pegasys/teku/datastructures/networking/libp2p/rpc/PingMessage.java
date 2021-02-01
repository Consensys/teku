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
import tech.pegasys.teku.ssz.backing.containers.ContainerType1;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

/** https://github.com/ethereum/eth2.0-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#metadata */
public class PingMessage extends Container1<PingMessage, UInt64View> implements RpcRequest {

  static class PingMessageType extends ContainerType1<PingMessage, UInt64View> {

    public PingMessageType() {
      super("PingMessage", namedType("seqNumber", BasicViewTypes.UINT64_TYPE));
    }

    @Override
    public PingMessage createFromBackingNode(TreeNode node) {
      return new PingMessage(this, node);
    }
  }

  @SszTypeDescriptor public static final PingMessageType TYPE = new PingMessageType();

  public PingMessage(PingMessageType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public PingMessage(UInt64 seqNumber) {
    super(TYPE, new UInt64View(seqNumber));
  }

  public UInt64 getSeqNumber() {
    return getField0().get();
  }

  @Override
  public int getMaximumRequestChunks() {
    return 1;
  }
}

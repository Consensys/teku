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

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container1;
import tech.pegasys.teku.ssz.backing.containers.ContainerType1;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public final class GoodbyeMessage extends Container1<GoodbyeMessage, UInt64View>
    implements RpcRequest, SimpleOffsetSerializable, SSZContainer {

  public static final ContainerType1<GoodbyeMessage, UInt64View> TYPE =
      new ContainerType1<>(BasicViewTypes.UINT64_TYPE) {
        @Override
        public GoodbyeMessage createFromBackingNode(TreeNode node) {
          return new GoodbyeMessage(this, node);
        }
      };

  public static final UInt64 REASON_CLIENT_SHUT_DOWN = UInt64.valueOf(1);
  public static final UInt64 REASON_IRRELEVANT_NETWORK = UInt64.valueOf(2);
  public static final UInt64 REASON_FAULT_ERROR = UInt64.valueOf(3);
  public static final UInt64 MIN_CUSTOM_REASON_CODE = UInt64.valueOf(128);

  // Custom reasons
  public static final UInt64 REASON_UNABLE_TO_VERIFY_NETWORK = UInt64.valueOf(128);
  public static final UInt64 REASON_TOO_MANY_PEERS = UInt64.valueOf(129);
  public static final UInt64 REASON_RATE_LIMITING = UInt64.valueOf(130);

  public GoodbyeMessage(ContainerType1<GoodbyeMessage, UInt64View> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public GoodbyeMessage(UInt64 reason) {
    super(TYPE, new UInt64View(reason));
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
  public String toString() {
    return MoreObjects.toStringHelper(this).add("reason", getReason()).toString();
  }

  @Override
  public int getMaximumRequestChunks() {
    return 0;
  }
}

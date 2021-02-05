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

import java.util.Collections;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.AbstractDelegateType;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.ListViewReadImpl;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;

public class EmptyMessage extends ListViewReadImpl<ByteView> implements RpcRequest {
  private static final ListViewType<ByteView> LIST_VIEW_TYPE =
      new ListViewType<>(BasicViewTypes.BYTE_TYPE, 0);

  public static class EmptyMessageType extends AbstractDelegateType<EmptyMessage> {
    private EmptyMessageType() {
      super(LIST_VIEW_TYPE);
    }

    @Override
    public EmptyMessage createFromBackingNode(TreeNode node) {
      return EMPTY_MESSAGE;
    }
  }

  public static final EmptyMessageType TYPE = new EmptyMessageType();
  public static final EmptyMessage EMPTY_MESSAGE = new EmptyMessage();

  private EmptyMessage() {
    super(ViewUtils.toListView(LIST_VIEW_TYPE, Collections.emptyList()));
  }

  @Override
  public String toString() {
    return "EmptyMessage{}";
  }

  @Override
  public int getMaximumRequestChunks() {
    return 1;
  }
}

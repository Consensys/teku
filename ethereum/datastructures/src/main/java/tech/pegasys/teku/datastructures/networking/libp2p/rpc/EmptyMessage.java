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
import tech.pegasys.teku.ssz.backing.schema.AbstractDelegateSszSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszListImpl;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class EmptyMessage extends SszListImpl<SszByte> implements RpcRequest {
  private static final SszListSchema<SszByte> LIST_VIEW_TYPE =
      new SszListSchema<>(SszPrimitiveSchemas.BYTE_SCHEMA, 0);

  public static class EmptyMessageSchema extends AbstractDelegateSszSchema<EmptyMessage> {
    private EmptyMessageSchema() {
      super(LIST_VIEW_TYPE);
    }

    @Override
    public EmptyMessage createFromBackingNode(TreeNode node) {
      return EMPTY_MESSAGE;
    }
  }

  public static final EmptyMessageSchema SSZ_SCHEMA = new EmptyMessageSchema();
  public static final EmptyMessage EMPTY_MESSAGE = new EmptyMessage();

  private EmptyMessage() {
    super(SszUtils.toSszList(LIST_VIEW_TYPE, Collections.emptyList()));
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

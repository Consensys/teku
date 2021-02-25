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

import tech.pegasys.teku.ssz.backing.schema.AbstractSszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszListImpl;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;

public class EmptyMessage extends SszListImpl<SszByte> implements RpcRequest {

  public static class EmptyMessageSchema extends AbstractSszListSchema<SszByte, EmptyMessage> {
    private EmptyMessageSchema() {
      super(SszPrimitiveSchemas.BYTE_SCHEMA, 0);
    }

    @Override
    public EmptyMessage createFromBackingNode(TreeNode node) {
      return EMPTY_MESSAGE;
    }
  }

  public static final EmptyMessageSchema SSZ_SCHEMA = new EmptyMessageSchema();
  public static final EmptyMessage EMPTY_MESSAGE = new EmptyMessage();

  private EmptyMessage() {
    super(SSZ_SCHEMA, SSZ_SCHEMA.getDefaultTree());
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

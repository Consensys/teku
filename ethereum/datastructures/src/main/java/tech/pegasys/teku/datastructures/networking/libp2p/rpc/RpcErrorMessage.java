/*
 * Copyright 2021 ConsenSys AG.
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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.schema.AbstractDelegateSszSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszListImpl;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class RpcErrorMessage extends SszListImpl<SszByte> implements SszList<SszByte> {

  public static final int MAX_ERROR_MESSAGE_LENGTH = 256;
  private static final Charset ERROR_MESSAGE_CHARSET = StandardCharsets.UTF_8;
  private static final SszListSchema<SszByte> LIST_VIEW_TYPE =
      new SszListSchema<>(SszPrimitiveSchemas.BYTE_SCHEMA, MAX_ERROR_MESSAGE_LENGTH);

  public static class RpcErrorMessageType extends AbstractDelegateSszSchema<RpcErrorMessage> {
    private RpcErrorMessageType() {
      super(LIST_VIEW_TYPE);
    }

    @Override
    public RpcErrorMessage createFromBackingNode(TreeNode node) {
      return new RpcErrorMessage(node);
    }
  }

  public static final RpcErrorMessageType SSZ_SCHEMA = new RpcErrorMessageType();

  public RpcErrorMessage(Bytes bytes) {
    super(SszUtils.toSszList(LIST_VIEW_TYPE, SszUtils.toSszByteList(LIST_VIEW_TYPE, bytes)));
  }

  private RpcErrorMessage(TreeNode node) {
    super(LIST_VIEW_TYPE, node);
  }

  public Bytes getData() {
    return SszUtils.getAllBytes(this);
  }

  @Override
  public String toString() {
    return new String(getData().toArrayUnsafe(), ERROR_MESSAGE_CHARSET);
  }
}

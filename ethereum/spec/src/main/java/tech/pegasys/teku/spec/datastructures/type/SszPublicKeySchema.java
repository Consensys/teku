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

package tech.pegasys.teku.spec.datastructures.type;

import tech.pegasys.teku.ssz.backing.schema.collections.impl.SszByteVectorSchemaImpl;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public class SszPublicKeySchema extends SszByteVectorSchemaImpl<SszPublicKey> {
  private static final int BLS_COMPRESSED_PUBLIC_KEY_SIZE = 48;

  public static final SszPublicKeySchema INSTANCE = new SszPublicKeySchema();

  private SszPublicKeySchema() {
    super(BLS_COMPRESSED_PUBLIC_KEY_SIZE);
  }

  @Override
  public SszPublicKey createFromBackingNode(TreeNode node) {
    return new SszPublicKey(node);
  }
}

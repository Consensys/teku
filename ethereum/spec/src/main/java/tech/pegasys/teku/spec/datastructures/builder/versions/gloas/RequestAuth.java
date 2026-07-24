/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.datastructures.builder.versions.gloas;

import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RequestAuth extends Container2<RequestAuth, SszByteList, SszUInt64> {

  protected RequestAuth(final RequestAuthSchema schema, final SszByteList data, final UInt64 slot) {
    super(schema, data, SszUInt64.of(slot));
  }

  protected RequestAuth(final RequestAuthSchema schema, final TreeNode backingTree) {
    super(schema, backingTree);
  }

  public SszByteList getData() {
    return getField0();
  }

  public UInt64 getSlot() {
    return getField1().get();
  }

  @Override
  public RequestAuthSchema getSchema() {
    return (RequestAuthSchema) super.getSchema();
  }
}

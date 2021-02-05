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

package tech.pegasys.teku.datastructures.state;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema3;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes4;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public class Fork extends Container3<Fork, SszBytes4, SszBytes4, SszUInt64> {

  public static class ForkSchema extends ContainerSchema3<Fork, SszBytes4, SszBytes4, SszUInt64> {

    public ForkSchema() {
      super(
          "Fork",
          namedSchema("previous_version", SszPrimitiveSchemas.BYTES4_SCHEMA),
          namedSchema("current_version", SszPrimitiveSchemas.BYTES4_SCHEMA),
          namedSchema("epoch", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public Fork createFromBackingNode(TreeNode node) {
      return new Fork(this, node);
    }
  }

  public static final ForkSchema SSZ_SCHEMA = new ForkSchema();

  private Fork(ForkSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Fork(Bytes4 previous_version, Bytes4 current_version, UInt64 epoch) {
    super(
        SSZ_SCHEMA,
        new SszBytes4(previous_version),
        new SszBytes4(current_version),
        new SszUInt64(epoch));
  }

  public Bytes4 getPrevious_version() {
    return getField0().get();
  }

  public Bytes4 getCurrent_version() {
    return getField1().get();
  }

  public UInt64 getEpoch() {
    return getField2().get();
  }
}

/*
 * Copyright 2020 ConsenSys AG.
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
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.Bytes4View;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.UInt64View;

public class EnrForkId extends Container3<EnrForkId, Bytes4View, Bytes4View, UInt64View> {

  public static class EnrForkIdType
      extends ContainerType3<EnrForkId, Bytes4View, Bytes4View, UInt64View> {

    public EnrForkIdType() {
      super(
          "EnrForkId",
          namedType("forkDigest", BasicViewTypes.BYTES4_TYPE),
          namedType("nextForkVersion", BasicViewTypes.BYTES4_TYPE),
          namedType("nextForkEpoch", BasicViewTypes.UINT64_TYPE));
    }

    @Override
    public EnrForkId createFromBackingNode(TreeNode node) {
      return new EnrForkId(this, node);
    }
  }

  public static final EnrForkIdType TYPE = new EnrForkIdType();

  private EnrForkId(EnrForkIdType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public EnrForkId(
      final Bytes4 forkDigest, final Bytes4 nextForkVersion, final UInt64 nextForkEpoch) {
    super(
        TYPE,
        new Bytes4View(forkDigest),
        new Bytes4View(nextForkVersion),
        new UInt64View(nextForkEpoch));
  }

  public Bytes4 getForkDigest() {
    return getField0().get();
  }

  public Bytes4 getNextForkVersion() {
    return getField1().get();
  }

  public UInt64 getNextForkEpoch() {
    return getField2().get();
  }
}

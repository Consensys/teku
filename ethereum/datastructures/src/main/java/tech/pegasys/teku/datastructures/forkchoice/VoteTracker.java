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

package tech.pegasys.teku.datastructures.forkchoice;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public class VoteTracker extends Container3<VoteTracker, SszBytes32, SszBytes32, SszUInt64> {

  public static class VoteTrackerType
      extends ContainerType3<VoteTracker, SszBytes32, SszBytes32, SszUInt64> {

    public VoteTrackerType() {
      super(
          "VoteTracker",
          namedSchema("currentRoot", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("nextRoot", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("nextEpoch", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public VoteTracker createFromBackingNode(TreeNode node) {
      return new VoteTracker(this, node);
    }
  }

  public static final VoteTrackerType TYPE = new VoteTrackerType();
  public static final VoteTracker DEFAULT = new VoteTracker();

  private VoteTracker(
      ContainerType3<VoteTracker, SszBytes32, SszBytes32, SszUInt64> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  private VoteTracker() {
    super(TYPE);
  }

  public VoteTracker(Bytes32 currentRoot, Bytes32 nextRoot, UInt64 nextEpoch) {
    super(TYPE, new SszBytes32(currentRoot), new SszBytes32(nextRoot), new SszUInt64(nextEpoch));
  }

  public Bytes32 getCurrentRoot() {
    return getField0().get();
  }

  public Bytes32 getNextRoot() {
    return getField1().get();
  }

  public UInt64 getNextEpoch() {
    return getField2().get();
  }
}

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

package tech.pegasys.teku.fuzz.input;

import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class BlockFuzzInput extends Container2<BlockFuzzInput, BeaconState, SignedBeaconBlock>
    implements SimpleOffsetSerializable, SSZContainer {

  @SszTypeDescriptor
  public static ContainerType2<BlockFuzzInput, BeaconState, SignedBeaconBlock> createType() {
    return ContainerType2
        .create(BeaconState.getSSZType(), SignedBeaconBlock.TYPE.get(), BlockFuzzInput::new);
  }

  private BlockFuzzInput(
      ContainerType2<BlockFuzzInput, BeaconState, SignedBeaconBlock> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public BlockFuzzInput(final BeaconStateImpl state, final SignedBeaconBlock signed_block) {
    super(createType(), state, signed_block);
  }

  // NOTE: empty constructor is needed for reflection/introspection
  public BlockFuzzInput() {
    super(createType());
  }

  public SignedBeaconBlock getSigned_block() {
    return getField1();
  }

  public BeaconState getState() {
    return getField0();
  }
}

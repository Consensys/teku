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

import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

/**
 * Note: BlockHeader fuzzing target accepts a block as input (not a SignedBeaconBlock or
 * BeaconBlockHeader)
 */
public class BlockHeaderFuzzInput extends Container2<BlockHeaderFuzzInput, BeaconState, BeaconBlock>
    implements SimpleOffsetSerializable, SSZContainer {

  @SszTypeDescriptor
  public static ContainerType2<BlockHeaderFuzzInput, BeaconState, BeaconBlock> createType() {
    return ContainerType2.create(
        BeaconState.getSSZType(), BeaconBlock.TYPE.get(), BlockHeaderFuzzInput::new);
  }

  private BlockHeaderFuzzInput(
      ContainerType2<BlockHeaderFuzzInput, BeaconState, BeaconBlock> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public BlockHeaderFuzzInput(final BeaconStateImpl state, final BeaconBlock block) {
    super(createType(), state, block);
  }

  // NOTE: empty constructor is needed for reflection/introspection
  public BlockHeaderFuzzInput() {
    super(createType());
  }

  public BeaconBlock getBlock() {
    return getField1();
  }

  public BeaconState getState() {
    return getField0();
  }
}

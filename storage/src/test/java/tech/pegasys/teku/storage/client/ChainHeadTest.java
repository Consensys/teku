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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class ChainHeadTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  public void equals_shouldBeEqualToCopy() {
    final SignedBlockAndState block = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final UInt64 forkChoiceSlot = UInt64.valueOf(1L);
    final ChainHead chainHead = ChainHead.create(block, forkChoiceSlot);

    final ChainHead otherChainHead = copy(chainHead);
    assertThat(chainHead).isEqualTo(otherChainHead);
  }

  @Test
  public void equals_shouldBNotBeEqualWhenForkChoiceSlotDiffers() {
    final SignedBlockAndState block = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final UInt64 forkChoiceSlot = UInt64.valueOf(1L);
    final ChainHead chainHead = ChainHead.create(block, forkChoiceSlot);

    final ChainHead otherChainHead = ChainHead.create(chainHead, forkChoiceSlot.plus(1));
    assertThat(chainHead).isNotEqualTo(otherChainHead);
  }

  @Test
  public void equals_shouldBNotBeEqualWhenBlocksDiffer() {
    final SignedBlockAndState block = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final SignedBlockAndState otherBlock = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    assertThat(block).isNotEqualTo(otherBlock);
    final UInt64 forkChoiceSlot = UInt64.valueOf(1L);

    final ChainHead chainHead = ChainHead.create(block, forkChoiceSlot);
    final ChainHead otherChainHead = ChainHead.create(otherBlock, forkChoiceSlot);

    assertThat(chainHead).isNotEqualTo(otherChainHead);
  }

  private ChainHead copy(ChainHead original) {
    final SignedBeaconBlock blockCopy = copy(original.getBlock(), SignedBeaconBlock.class);
    final BeaconState stateCopy =
        copy((BeaconStateImpl) original.getState(), BeaconStateImpl.class);
    final SignedBlockAndState blockAndStateCopy = new SignedBlockAndState(blockCopy, stateCopy);
    final UInt64 forkChoiceCopy = copy(original.getForkChoiceSlot());
    return ChainHead.create(blockAndStateCopy, forkChoiceCopy);
  }

  private <T extends SimpleOffsetSerializable> T copy(final T original, final Class<T> objType) {
    final Bytes serialized = SimpleOffsetSerializer.serialize(original);
    return SimpleOffsetSerializer.deserialize(serialized, objType);
  }

  private UInt64 copy(final UInt64 value) {
    return UInt64.valueOf(value.longValue());
  }
}

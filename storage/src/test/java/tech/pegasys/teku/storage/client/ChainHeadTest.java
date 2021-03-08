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
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

public class ChainHeadTest {
  private final Spec spec = SpecFactory.createMinimal();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  public void equals_shouldBeEqualToCopy() {
    final SignedBlockAndState block = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final UInt64 forkChoiceSlot = UInt64.valueOf(1L);
    final ChainHead chainHead = ChainHead.create(block, forkChoiceSlot, spec);

    final ChainHead otherChainHead = copy(chainHead);
    assertThat(chainHead).isEqualTo(otherChainHead);
  }

  @Test
  public void equals_shouldBNotBeEqualWhenForkChoiceSlotDiffers() {
    final SignedBlockAndState block = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final UInt64 forkChoiceSlot = UInt64.valueOf(1L);
    final ChainHead chainHead = ChainHead.create(block, forkChoiceSlot, spec);

    final ChainHead otherChainHead = ChainHead.create(chainHead, forkChoiceSlot.plus(1), spec);
    assertThat(chainHead).isNotEqualTo(otherChainHead);
  }

  @Test
  public void equals_shouldBNotBeEqualWhenBlocksDiffer() {
    final SignedBlockAndState block = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final SignedBlockAndState otherBlock = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    assertThat(block).isNotEqualTo(otherBlock);
    final UInt64 forkChoiceSlot = UInt64.valueOf(1L);

    final ChainHead chainHead = ChainHead.create(block, forkChoiceSlot, spec);
    final ChainHead otherChainHead = ChainHead.create(otherBlock, forkChoiceSlot, spec);

    assertThat(chainHead).isNotEqualTo(otherChainHead);
  }

  @Test
  void findCommonAncestor_atGenesisBlock() {
    final ChainBuilder chainBuilder = ChainBuilder.createDefault();
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final SignedBlockAndState block2 = chainBuilder.generateBlockAtSlot(2);
    final ChainHead chainHeadA = ChainHead.create(genesis, UInt64.valueOf(10), spec);
    final ChainHead chainHeadB = ChainHead.create(block2, UInt64.valueOf(12), spec);
    assertThat(chainHeadA.findCommonAncestor(chainHeadB)).isEqualTo(UInt64.ZERO);
    assertThat(chainHeadB.findCommonAncestor(chainHeadA)).isEqualTo(UInt64.ZERO);
  }

  @Test
  void findCommonAncestor_headBlockEmptyVsFull() {
    final ChainBuilder chainBuilder = ChainBuilder.createDefault();
    chainBuilder.generateGenesis();
    final SignedBlockAndState block1 = chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState block2 = chainBuilder.generateBlockAtSlot(2);
    final ChainHead chainHeadA = ChainHead.create(block1, UInt64.valueOf(2), spec);
    final ChainHead chainHeadB = ChainHead.create(block2, UInt64.valueOf(2), spec);
    assertThat(chainHeadA.findCommonAncestor(chainHeadB)).isEqualTo(UInt64.ONE);
    assertThat(chainHeadB.findCommonAncestor(chainHeadA)).isEqualTo(UInt64.ONE);
  }

  @Test
  void findCommonAncestor_differingChains() {
    final ChainBuilder chainBuilder = ChainBuilder.createDefault();
    chainBuilder.generateGenesis();
    chainBuilder.generateBlockAtSlot(2);
    final ChainBuilder fork = chainBuilder.fork();
    chainBuilder.generateBlocksUpToSlot(4);
    final SignedBlockAndState chainHead = chainBuilder.generateBlockAtSlot(5);

    // Fork skips slot 3 so the chains are different
    fork.generateBlockAtSlot(4);
    fork.generateBlocksUpToSlot(6);
    final SignedBlockAndState forkHead = fork.generateBlockAtSlot(7);

    final ChainHead chainHeadA = ChainHead.create(chainHead, UInt64.valueOf(8), spec);
    final ChainHead chainHeadB = ChainHead.create(forkHead, UInt64.valueOf(9), spec);
    assertThat(chainHeadA.findCommonAncestor(chainHeadB)).isEqualTo(UInt64.valueOf(2));
    assertThat(chainHeadB.findCommonAncestor(chainHeadA)).isEqualTo(UInt64.valueOf(2));
  }

  @Test
  void findCommonAncestor_differingBeyondHistoricRoots() {
    final ChainBuilder chainBuilder = ChainBuilder.createDefault();
    chainBuilder.generateGenesis();
    final ChainBuilder fork = chainBuilder.fork();

    final SignedBlockAndState chainHead =
        chainBuilder.generateBlockAtSlot(spec.getSlotsPerHistoricalRoot(fork.getLatestSlot()) + 2);

    // Fork skips slot 1 so the chains are different
    fork.generateBlockAtSlot(1);
    final SignedBlockAndState forkHead =
        fork.generateBlockAtSlot(spec.getSlotsPerHistoricalRoot(fork.getLatestSlot()) + 2);

    final ChainHead chainHeadA = ChainHead.create(chainHead, chainHead.getSlot(), spec);
    final ChainHead chainHeadB = ChainHead.create(forkHead, forkHead.getSlot(), spec);
    assertThat(chainHeadA.findCommonAncestor(chainHeadB)).isEqualTo(UInt64.ZERO);
    assertThat(chainHeadB.findCommonAncestor(chainHeadA)).isEqualTo(UInt64.ZERO);
  }

  @SuppressWarnings("unchecked")
  private ChainHead copy(ChainHead original) {
    final SignedBeaconBlock blockCopy =
        copy(original.getSignedBeaconBlock().orElseThrow(), SignedBeaconBlock.SSZ_SCHEMA.get());
    final BeaconState stateCopy =
        copy(
            original.getState(),
            (SszSchema<BeaconState>) spec.getGenesisSchemaDefinitions().getBeaconStateSchema());
    final SignedBlockAndState blockAndStateCopy = new SignedBlockAndState(blockCopy, stateCopy);
    final UInt64 forkChoiceCopy = copy(original.getForkChoiceSlot());
    return ChainHead.create(blockAndStateCopy, forkChoiceCopy, spec);
  }

  private <T extends SszData> T copy(final T original, final SszSchema<T> objType) {
    final Bytes serialized = original.sszSerialize();
    return objType.sszDeserialize(serialized);
  }

  private UInt64 copy(final UInt64 value) {
    return UInt64.valueOf(value.longValue());
  }
}

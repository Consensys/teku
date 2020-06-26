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

package tech.pegasys.teku.datastructures.blocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

public class BlockTreeTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  public void build_singleChain() {
    final SignedBeaconBlock baseBlock = dataStructureUtil.randomSignedBeaconBlock(0);
    final List<SignedBeaconBlock> chain =
        dataStructureUtil.randomSignedBeaconBlockSequence(baseBlock, 5);

    final BlockTree blockTree = BlockTree.builder().rootBlock(baseBlock).blocks(chain).build();

    // All blocks should be available
    assertThat(blockTree.getBlockCount()).isEqualTo(chain.size() + 1);
    assertThat(blockTree.containsBlock(baseBlock.getRoot())).isTrue();
    for (SignedBeaconBlock block : chain) {
      assertThat(blockTree.containsBlock(block.getRoot())).isTrue();
    }
  }

  @Test
  public void build_ignoreRandomBlock() {
    final SignedBeaconBlock baseBlock = dataStructureUtil.randomSignedBeaconBlock(0);
    final List<SignedBeaconBlock> chain =
        dataStructureUtil.randomSignedBeaconBlockSequence(baseBlock, 5);
    final SignedBeaconBlock randomBlock = dataStructureUtil.randomSignedBeaconBlock(2);

    final BlockTree blockTree =
        BlockTree.builder().rootBlock(baseBlock).blocks(chain).block(randomBlock).build();

    // Only valid blocks should be available
    assertThat(blockTree.getBlockCount()).isEqualTo(chain.size() + 1);
    assertThat(blockTree.containsBlock(baseBlock.getRoot())).isTrue();
    for (SignedBeaconBlock block : chain) {
      assertThat(blockTree.containsBlock(block.getRoot())).isTrue();
    }
  }

  @Test
  public void build_withForks() {
    final SignedBeaconBlock baseBlock = dataStructureUtil.randomSignedBeaconBlock(0);
    final List<SignedBeaconBlock> chain =
        dataStructureUtil.randomSignedBeaconBlockSequence(baseBlock, 5);
    final List<SignedBeaconBlock> chain2 =
        dataStructureUtil.randomSignedBeaconBlockSequence(baseBlock, 3);

    final BlockTree blockTree =
        BlockTree.builder().rootBlock(baseBlock).blocks(chain).blocks(chain2).build();

    // All blocks should be available
    final List<SignedBeaconBlock> allBlocks = new ArrayList<>(chain);
    allBlocks.addAll(chain2);
    allBlocks.add(baseBlock);
    assertThat(blockTree.getBlockCount()).isEqualTo(allBlocks.size());
    for (SignedBeaconBlock block : allBlocks) {
      assertThat(blockTree.containsBlock(block.getRoot())).isTrue();
    }
  }

  @Test
  public void build_withInvalidFork() {
    final SignedBeaconBlock genesis = dataStructureUtil.randomSignedBeaconBlock(0);
    final List<SignedBeaconBlock> chain =
        dataStructureUtil.randomSignedBeaconBlockSequence(genesis, 8);
    final SignedBeaconBlock baseBlock = chain.get(0);
    final List<SignedBeaconBlock> invalidFork =
        dataStructureUtil.randomSignedBeaconBlockSequence(genesis, 3);

    final BlockTree blockTree =
        BlockTree.builder().rootBlock(baseBlock).blocks(chain).blocks(invalidFork).build();

    // Only valid blocks should be available
    assertThat(blockTree.getBlockCount()).isEqualTo(chain.size());
    for (SignedBeaconBlock block : chain) {
      assertThat(blockTree.containsBlock(block.getRoot())).isTrue();
    }
  }

  @Test
  public void build_empty() {
    final SignedBeaconBlock baseBlock = dataStructureUtil.randomSignedBeaconBlock(0);

    final BlockTree blockTree = BlockTree.builder().rootBlock(baseBlock).build();

    assertThat(blockTree.getBlockCount()).isEqualTo(1);
    assertThat(blockTree.containsBlock(baseBlock.getRoot())).isTrue();
  }
}

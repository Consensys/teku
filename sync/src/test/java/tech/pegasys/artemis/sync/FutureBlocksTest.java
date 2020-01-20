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

package tech.pegasys.artemis.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;

public class FutureBlocksTest {

  private final FutureBlocks futureBlocks = new FutureBlocks();

  @Test
  public void add() {
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(5, 1);

    futureBlocks.add(block);
    assertThat(futureBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.contains(block)).isTrue();
  }

  @Test
  public void prune_nothingToPrune() {
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(5, 1);

    futureBlocks.add(block);

    final UnsignedLong priorSlot = block.getSlot().minus(UnsignedLong.ONE);
    final List<SignedBeaconBlock> pruned = futureBlocks.prune(priorSlot);
    assertThat(pruned).isEmpty();

    assertThat(futureBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.contains(block)).isTrue();
  }

  @Test
  public void prune_blockAtSlot() {
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(5, 1);

    futureBlocks.add(block);

    final List<SignedBeaconBlock> pruned = futureBlocks.prune(block.getSlot());
    assertThat(pruned).containsExactly(block);
    assertThat(futureBlocks.size()).isEqualTo(0);
  }

  @Test
  public void prune_blockPriorToSlot() {
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(5, 1);

    futureBlocks.add(block);

    final List<SignedBeaconBlock> pruned =
        futureBlocks.prune(block.getSlot().plus(UnsignedLong.ONE));
    assertThat(pruned).containsExactly(block);
    assertThat(futureBlocks.size()).isEqualTo(0);
  }
}

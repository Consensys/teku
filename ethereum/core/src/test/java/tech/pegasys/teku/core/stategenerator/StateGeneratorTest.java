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

package tech.pegasys.teku.core.stategenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StateGeneratorTest {
  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);
  private final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);

  @Test
  public void regenerateStateForBlock_failOnMissingBlocks() {
    testGeneratorWithMissingBlock(
        (generator, missingBlock) -> {
          SafeFuture<SignedBlockAndState> result =
              generator.regenerateStateForBlock(missingBlock.getRoot());
          assertThatThrownBy(result::get)
              .hasCauseInstanceOf(IllegalStateException.class)
              .hasMessageContaining(
                  "Failed to retrieve 1 / 3 blocks building on state at slot 0: "
                      + missingBlock.getRoot());
        });
  }

  @Test
  public void regenerateStateForBlock_blockPastTargetIsMissing() {
    testGeneratorWithMissingBlock(
        (generator, missingBlock) -> {
          SignedBlockAndState target =
              chainBuilder.getBlockAndStateAtSlot(missingBlock.getSlot().minus(UInt64.ONE));
          SafeFuture<SignedBlockAndState> result =
              generator.regenerateStateForBlock(target.getRoot());
          assertThat(result).isCompletedWithValue(target);
        });
  }

  private void testGeneratorWithMissingBlock(
      BiConsumer<StateGenerator, SignedBeaconBlock> processor) {
    // Build a small chain
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(5);
    final Map<Bytes32, SignedBeaconBlock> blockMap =
        chainBuilder
            .streamBlocksAndStates()
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));

    final HashTree tree =
        HashTree.builder().rootHash(genesis.getRoot()).blocks(blockMap.values()).build();
    // Create block provider that is missing some blocks
    final SignedBeaconBlock missingBlock = chainBuilder.getBlockAtSlot(genesis.getSlot().plus(2));
    blockMap.remove(missingBlock.getRoot());
    final BlockProvider blockProvider = BlockProvider.fromMap(blockMap);

    final StateGenerator generator = StateGenerator.create(tree, genesis, blockProvider);
    processor.accept(generator, missingBlock);
  }

  public static Stream<Arguments> getParameters() {
    Stream.Builder<Arguments> builder = Stream.builder();

    final List<Integer> stateCacheSizes = List.of(0, 1, 100);
    final List<Integer> blockBatchSizes = List.of(1, 5, 1000);
    for (Integer stateCacheSize : stateCacheSizes) {
      for (Integer blockBatchSize : blockBatchSizes) {
        builder.add(Arguments.of(stateCacheSize, blockBatchSize));
      }
    }

    return builder.build();
  }
}

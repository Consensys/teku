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

package tech.pegasys.teku.statetransition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.ChainBuilder.BlockOptions;
import tech.pegasys.teku.core.StateGenerator;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

@Disabled
@ExtendWith(BouncyCastleExtension.class)
class StateGeneratorTest {
  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);
  private final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);
  private final StateGenerator stateGenerator = new StateGenerator();

  @Test
  public void produceStatesForBlocks_validChainFromGenesis() throws StateTransitionException {
    // Build a small chain
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(10);
    final List<SignedBlockAndState> newBlocksAndStates =
        chainBuilder
            .streamBlocksAndStates(
                genesis.getSlot().plus(UnsignedLong.ONE), chainBuilder.getLatestSlot())
            .collect(Collectors.toList());

    final List<SignedBeaconBlock> newBlocks =
        newBlocksAndStates.stream().map(SignedBlockAndState::getBlock).collect(Collectors.toList());
    final Map<Bytes32, BeaconState> expectedResult =
        newBlocksAndStates.stream()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
    expectedResult.put(genesis.getRoot(), genesis.getState());

    final Map<Bytes32, BeaconState> result =
        stateGenerator.produceStatesForBlocks(genesis.getRoot(), genesis.getState(), newBlocks);
    assertThat(result.size()).isEqualTo(expectedResult.size());
    assertThat(result).isEqualToComparingFieldByField(expectedResult);
  }

  @Test
  public void produceStatesForBlocks_validPostGenesisChain() throws StateTransitionException {
    // Build a small chain
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(10);
    final SignedBlockAndState baseBlock = chainBuilder.getBlockAndStateAtSlot(5);
    final List<SignedBlockAndState> newBlocksAndStates =
        chainBuilder
            .streamBlocksAndStates(
                baseBlock.getSlot().plus(UnsignedLong.ONE), chainBuilder.getLatestSlot())
            .collect(Collectors.toList());

    final List<SignedBeaconBlock> newBlocks =
        newBlocksAndStates.stream().map(SignedBlockAndState::getBlock).collect(Collectors.toList());
    final Map<Bytes32, BeaconState> expectedResult =
        newBlocksAndStates.stream()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
    expectedResult.put(baseBlock.getRoot(), baseBlock.getState());

    final Map<Bytes32, BeaconState> result =
        stateGenerator.produceStatesForBlocks(baseBlock.getRoot(), baseBlock.getState(), newBlocks);
    assertThat(result.size()).isEqualTo(expectedResult.size());
    assertThat(result).isEqualToComparingFieldByField(expectedResult);
  }

  @Test
  public void produceStatesForBlocks_withForkBlocks() throws StateTransitionException {
    // Build a small chain
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(5);
    final ChainBuilder fork = chainBuilder.fork();

    chainBuilder.generateBlocksUpToSlot(10);
    // Fork chain skips a block
    fork.generateBlockAtSlot(7);
    fork.generateBlocksUpToSlot(10);

    // Set base block so that fork blocks cannot be reconstructed
    final SignedBlockAndState baseBlock = chainBuilder.getBlockAndStateAtSlot(6);

    final List<SignedBlockAndState> newBlocksAndStates =
        chainBuilder
            .streamBlocksAndStates(
                baseBlock.getSlot().plus(UnsignedLong.ONE), chainBuilder.getLatestSlot())
            .collect(Collectors.toList());
    final List<SignedBlockAndState> newForkBlocks =
        fork.streamBlocksAndStates(
                baseBlock.getSlot().plus(UnsignedLong.ONE), chainBuilder.getLatestSlot())
            .collect(Collectors.toList());

    final Set<SignedBeaconBlock> newBlocks =
        Streams.concat(newBlocksAndStates.stream(), newForkBlocks.stream())
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toSet());
    // Sanity check that we included some fork blocks
    assertThat(newBlocks.size()).isGreaterThan(newBlocksAndStates.size());
    final Map<Bytes32, BeaconState> expectedResult =
        newBlocksAndStates.stream()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
    expectedResult.put(baseBlock.getRoot(), baseBlock.getState());

    final Map<Bytes32, BeaconState> result =
        stateGenerator.produceStatesForBlocks(baseBlock.getRoot(), baseBlock.getState(), newBlocks);
    assertThat(result.size()).isEqualTo(expectedResult.size());
    assertThat(result).isEqualToComparingFieldByField(expectedResult);
  }

  @Test
  public void produceStatesForBlocks_emptyNewBlockCollection() {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();

    final Map<Bytes32, BeaconState> expectedResult = Map.of(genesis.getRoot(), genesis.getState());

    final Map<Bytes32, BeaconState> result =
        stateGenerator.produceStatesForBlocks(
            genesis.getRoot(), genesis.getState(), Collections.emptyList());
    assertThat(result.size()).isEqualTo(expectedResult.size());
    assertThat(result).isEqualToComparingFieldByField(expectedResult);
  }

  @Test
  public void produceStatesForBlocks_invalidBaseState() throws StateTransitionException {
    final DataStructureUtil rand = new DataStructureUtil();

    // Build a small chain
    chainBuilder.generateGenesis();
    final UnsignedLong lastCommonSlot = compute_start_slot_at_epoch(UnsignedLong.valueOf(2));
    chainBuilder.generateBlocksUpToSlot(lastCommonSlot);
    // Fork chain and create 2 divergent chains
    final ChainBuilder fork = chainBuilder.fork();
    // Make sure chains diverge at this point
    final UnsignedLong firstDivergentSlot = lastCommonSlot.plus(UnsignedLong.ONE);
    chainBuilder.generateBlockAtSlot(
        firstDivergentSlot, BlockOptions.create().setEth1Data(rand.randomEth1Data()));
    fork.generateBlockAtSlot(
        firstDivergentSlot, BlockOptions.create().setEth1Data(rand.randomEth1Data()));
    // Add more blocks
    final UnsignedLong lastSlot = firstDivergentSlot.plus(UnsignedLong.valueOf(10));
    chainBuilder.generateBlocksUpToSlot(lastSlot);
    fork.generateBlocksUpToSlot(lastSlot);

    // Try to regenerate states using the wrong base state
    final Bytes32 baseRoot = chainBuilder.getBlockAndStateAtSlot(lastCommonSlot).getRoot();
    final BeaconState baseState = fork.getStateAtSlot(firstDivergentSlot);
    final List<SignedBeaconBlock> newBlocks =
        chainBuilder
            .streamBlocksAndStates(firstDivergentSlot)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    assertThatThrownBy(() -> stateGenerator.produceStatesForBlocks(baseRoot, baseState, newBlocks))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to produce state for block at slot " + firstDivergentSlot);
  }
}

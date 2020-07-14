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

package tech.pegasys.teku.protoarray;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.util.config.Constants;

public class ProtoArrayChainBuilder {
  private static UnsignedLong GENESIS_EPOCH = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);

  private final DataStructureUtil dataStructureUtil;
  private final ProtoArray protoArray;
  private final NavigableMap<UnsignedLong, BeaconBlock> chain;

  private ProtoArrayChainBuilder(
      ProtoArray protoArray,
      DataStructureUtil dataStructureUtil,
      Map<UnsignedLong, BeaconBlock> chain) {
    this.protoArray = protoArray;
    this.dataStructureUtil = dataStructureUtil;
    this.chain = new TreeMap<>(chain);
  }

  public static ProtoArrayChainBuilder createGenesisChain() {
    final ProtoArrayChainBuilder chainBuilder = createEmpty();
    chainBuilder.initializeGenesis();
    return chainBuilder;
  }

  public static ProtoArrayChainBuilder createEmpty() {
    final ProtoArray protoArray =
        new ProtoArray(0, GENESIS_EPOCH, GENESIS_EPOCH, new ArrayList<>(), new HashMap<>());
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final ProtoArrayChainBuilder chainBuilder =
        new ProtoArrayChainBuilder(protoArray, dataStructureUtil, new HashMap<>());
    return chainBuilder;
  }

  public ProtoArray protoArray() {
    return protoArray;
  }

  public ProtoArrayChainBuilder fork() {
    return new ProtoArrayChainBuilder(protoArray, dataStructureUtil, chain);
  }

  public void initializeGenesis() {
    checkState(chain.size() == 0, "Chain must be empty");
    final BeaconBlock genesis = dataStructureUtil.randomBeaconBlock(Constants.GENESIS_SLOT);
    processBlock(genesis);
  }

  public void advanceToSlot(final long slot) {
    advanceToSlot(UnsignedLong.valueOf(slot));
  }

  public void advanceToSlot(final UnsignedLong slot) {
    assertChainIsInitialized();
    while (getLatestSlot().compareTo(slot) < 0) {
      advanceChain();
    }
  }

  public void advanceChain() {
    assertChainIsInitialized();
    BeaconBlock prevBlock = getLastBlock().orElseThrow();
    final BeaconBlock nextBlock =
        dataStructureUtil.randomBeaconBlock(
            prevBlock.getSlot().plus(UnsignedLong.ONE).longValue(), prevBlock.hash_tree_root());
    processBlock(nextBlock);
  }

  public UnsignedLong getLatestSlot() {
    return getLastBlock()
        .map(BeaconBlock::getSlot)
        .orElseThrow(() -> new IllegalStateException("Chain is empty"));
  }

  public BeaconBlock getBlockAtSlot(final long slot) {
    return getBlockAtSlot(UnsignedLong.valueOf(slot));
  }

  public BeaconBlock getBlockAtSlot(final UnsignedLong slot) {
    return chain.get(slot);
  }

  private void processBlock(final BeaconBlock block) {
    protoArray.onBlock(
        block.getSlot(),
        block.hash_tree_root(),
        block.getParent_root(),
        block.getState_root(),
        protoArray.getJustifiedEpoch(),
        protoArray.getFinalizedEpoch());
    chain.put(block.getSlot(), block);
  }

  private Optional<BeaconBlock> getLastBlock() {
    if (chain.size() == 0) {
      return Optional.empty();
    }
    return Optional.of(chain.lastEntry().getValue());
  }

  private void assertChainIsInitialized() {
    checkState(chain.size() > 0, "Must initialize genesis");
  }
}

/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;

public class RandomChainBuilderForkChoiceStrategy implements ReadOnlyForkChoiceStrategy {

  private final RandomChainBuilder chainBuilder;
  private UInt64 prunePriorToSlot = UInt64.ZERO;

  public RandomChainBuilderForkChoiceStrategy(final RandomChainBuilder chainBuilder) {
    this.chainBuilder = chainBuilder;
  }

  /**
   * Prune available block prior to the given slot
   *
   * @param slot
   */
  public void prune(final UInt64 slot) {
    this.prunePriorToSlot = slot;
  }

  @Override
  public Optional<UInt64> blockSlot(final Bytes32 blockRoot) {
    return getBlock(blockRoot).map(SignedBeaconBlock::getSlot);
  }

  @Override
  public Optional<Bytes32> blockParentRoot(final Bytes32 blockRoot) {
    return getBlock(blockRoot).map(SignedBeaconBlock::getParentRoot);
  }

  @Override
  public Optional<Bytes32> getAncestor(final Bytes32 blockRoot, final UInt64 slot) {
    if (getBlock(blockRoot).isEmpty()) {
      return Optional.empty();
    }
    return getBlock(slot).map(SignedBeaconBlock::getRoot);
  }

  @Override
  public Set<Bytes32> getBlockRootsAtSlot(final UInt64 slot) {
    final Optional<Bytes32> maybeRoot = getBlock(slot).map(SignedBeaconBlock::getRoot);
    if (maybeRoot.isEmpty()) {
      return Collections.emptySet();
    }
    final Set<Bytes32> output = new HashSet<>();
    output.add(maybeRoot.get());
    return output;
  }

  @Override
  public Map<Bytes32, UInt64> getChainHeads() {
    return chainBuilder
        .getChainHead()
        .map(h -> Map.of(h.getRoot(), h.getSlot()))
        .orElse(Collections.emptyMap());
  }

  @Override
  public Map<Bytes32, UInt64> getOptimisticChainHeads() {
    return Collections.emptyMap();
  }

  @Override
  public boolean contains(final Bytes32 blockRoot) {
    return getBlock(blockRoot).isPresent();
  }

  private Optional<SignedBeaconBlock> getBlock(final Bytes32 root) {
    return chainBuilder
        .getBlock(root)
        .filter(b -> b.getSlot().isGreaterThanOrEqualTo(prunePriorToSlot));
  }

  private Optional<SignedBeaconBlock> getBlock(final UInt64 slot) {
    return chainBuilder
        .getBlock(slot)
        .filter(b -> b.getSlot().isGreaterThanOrEqualTo(prunePriorToSlot));
  }
}

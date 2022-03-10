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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
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
  public Optional<Bytes32> executionBlockHash(final Bytes32 blockRoot) {
    return getBlock(blockRoot)
        .map(
            block -> {
              final BeaconBlockBody blockBody = block.getMessage().getBody();
              return blockBody instanceof BeaconBlockBodyBellatrix
                  ? ((BeaconBlockBodyBellatrix) blockBody).getExecutionPayload().getBlockHash()
                  : null;
            });
  }

  @Override
  public Optional<Bytes32> getAncestor(final Bytes32 blockRoot, final UInt64 slot) {
    if (getBlock(blockRoot).isEmpty()) {
      return Optional.empty();
    }
    return getBlock(slot).map(SignedBeaconBlock::getRoot);
  }

  @Override
  public Optional<SlotAndBlockRoot> findCommonAncestor(
      final Bytes32 blockRoot1, final Bytes32 blockRoot2) {
    return Optional.empty();
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
  public List<ProtoNodeData> getChainHeads() {
    return chainBuilder
        .getChainHead()
        .map(
            h ->
                List.of(
                    new ProtoNodeData(
                        h.getSlot(),
                        h.getRoot(),
                        h.getParentRoot(),
                        h.getStateRoot(),
                        h.getExecutionBlockHash().orElse(Bytes32.ZERO),
                        false)))
        .orElse(Collections.emptyList());
  }

  @Override
  public Optional<Bytes32> getOptimisticallySyncedTransitionBlockRoot(final Bytes32 head) {
    return Optional.empty();
  }

  @Override
  public List<Map<String, Object>> getNodeData() {
    return Collections.emptyList();
  }

  @Override
  public boolean contains(final Bytes32 blockRoot) {
    return getBlock(blockRoot).isPresent();
  }

  @Override
  public Optional<Boolean> isOptimistic(final Bytes32 blockRoot) {
    return Optional.of(false);
  }

  @Override
  public boolean isFullyValidated(Bytes32 blockRoot) {
    return true;
  }

  @Override
  public Optional<ProtoNodeData> getBlockData(final Bytes32 blockRoot) {
    return getBlock(blockRoot)
        .map(
            block ->
                new ProtoNodeData(
                    block.getSlot(),
                    block.getRoot(),
                    block.getParentRoot(),
                    block.getStateRoot(),
                    block
                        .getMessage()
                        .getBody()
                        .getOptionalExecutionPayload()
                        .map(ExecutionPayload::getBlockHash)
                        .orElse(Bytes32.ZERO),
                    false));
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

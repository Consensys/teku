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

package tech.pegasys.teku.core.lookup;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.util.async.SafeFuture;

@FunctionalInterface
public interface BlockProvider {

  BlockProvider NOOP = (roots) -> SafeFuture.completedFuture(Collections.emptyMap());

  static BlockProvider withKnownBlocks(
      final BlockProvider blockProvider, final Map<Bytes32, SignedBeaconBlock> knownBlocks) {
    return (final Set<Bytes32> blockRoots) -> {
      final Map<Bytes32, SignedBeaconBlock> blocks =
          blockRoots.stream()
              .map(knownBlocks::get)
              .filter(Objects::nonNull)
              .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
      final Set<Bytes32> remainingRoots = Sets.difference(blockRoots, blocks.keySet());
      if (remainingRoots.size() == 0) {
        return SafeFuture.completedFuture(blocks);
      }

      // Look up missing blocks
      return blockProvider
          .getBlocks(remainingRoots)
          .thenApply(
              retrieved -> {
                blocks.putAll(retrieved);
                return blocks;
              });
    };
  }

  SafeFuture<Map<Bytes32, SignedBeaconBlock>> getBlocks(final Set<Bytes32> blockRoots);

  default SafeFuture<Map<Bytes32, SignedBeaconBlock>> getBlocks(final List<Bytes32> blockRoots) {
    return getBlocks(new HashSet<>(blockRoots));
  }

  default SafeFuture<Optional<SignedBeaconBlock>> getBlock(final Bytes32 blockRoot) {
    return getBlocks(Set.of(blockRoot))
        .thenApply(blocks -> Optional.ofNullable(blocks.get(blockRoot)));
  }
}

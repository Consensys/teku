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

package tech.pegasys.teku.storage.store;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.stategenerator.StateGenerator;
import tech.pegasys.teku.core.stategenerator.StateHandler;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.hashtree.HashLink;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.util.async.SafeFuture;

interface StateProviderFactory {
  Logger LOG = LogManager.getLogger();

  StateProvider create(final BlockProvider blockProvider);

  // TODO - remove this method
  static StateProviderFactory createFromBlocks(
      final SignedBlockAndState finalized, final Collection<SignedBeaconBlock> blocks) {
    final List<HashLink> hashLinks =
        blocks.stream()
            .map(b -> new HashLink(b.getRoot(), b.getParent_root()))
            .collect(Collectors.toList());
    return createDefault(finalized, hashLinks);
  }

  static StateProviderFactory createDefault(
      final SignedBlockAndState finalized, final List<HashLink> blockLinks) {
    return (blockProvider) -> {
      final HashTree tree =
          HashTree.builder().rootHash(finalized.getRoot()).childAndParentRoots(blockLinks).build();
      final StateGenerator stateGenerator = StateGenerator.create(tree, finalized, blockProvider);

      if (tree.getBlockCount() < blockLinks.size()) {
        // This should be an error, but keeping this as a warning now for backwards-compatibility
        // reasons.  Some existing databases may have unpruned fork blocks, and could become
        // unusable
        // if we throw here.  In the future, we should convert this to an error.
        LOG.warn("Ignoring {} non-canonical blocks", blockLinks.size() - tree.getBlockCount());
      }

      return stateGenerator::regenerateAllStates;
    };
  }

  interface StateProvider {
    SafeFuture<?> provide(StateHandler stateHandler);
  }
}

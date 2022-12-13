/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;

public interface KvStoreCombinedDaoBlinded extends KvStoreCombinedDaoCommon {

  @MustBeClosed
  HotUpdaterBlinded hotUpdaterBlinded();

  @MustBeClosed
  FinalizedUpdaterBlinded finalizedUpdaterBlinded();

  @MustBeClosed
  CombinedUpdaterBlinded combinedUpdaterBlinded();

  List<SignedBeaconBlock> getBlindedNonCanonicalBlocksAtSlot(UInt64 slot);

  Optional<SignedBeaconBlock> getBlindedBlock(Bytes32 root);

  Optional<Bytes> getExecutionPayload(Bytes32 root);

  Optional<UInt64> getEarliestBlindedBlockSlot();

  Optional<SignedBeaconBlock> getEarliestBlindedBlock();

  Optional<SignedBeaconBlock> getLatestBlindedBlockAtSlot(UInt64 slot);

  Optional<Bytes32> getFinalizedBlockRootAtSlot(UInt64 slot);

  Optional<? extends SignedBeaconBlock> getNonCanonicalBlock(Bytes32 root);

  @MustBeClosed
  Stream<Bytes32> streamFinalizedBlockRoots(UInt64 startSlot, UInt64 endSlot);

  @MustBeClosed
  Stream<Map.Entry<Bytes32, SignedBeaconBlock>> streamUnblindedNonCanonicalBlocks();

  @MustBeClosed
  Stream<Map.Entry<Bytes32, UInt64>> streamUnblindedFinalizedBlockRoots();

  Stream<SignedBeaconBlock> streamBlindedBlocks();

  @MustBeClosed
  Stream<Map.Entry<Bytes, Bytes>> streamBlindedHotBlocksAsSsz();

  interface CombinedUpdaterBlinded
      extends HotUpdaterBlinded, FinalizedUpdaterBlinded, CombinedUpdaterCommon {}

  interface HotUpdaterBlinded extends HotUpdaterCommon {
    void addHotBlockCheckpointEpochs(Bytes32 blockRoot, BlockCheckpoints blockCheckpoints);

    default void addCheckpointEpochs(final Map<Bytes32, BlockAndCheckpoints> blocks) {
      blocks.forEach((k, v) -> addHotBlockCheckpointEpochs(k, v.getBlockCheckpoints()));
    }

    void pruneHotBlockContext(Bytes32 blockRoot);
  }

  interface FinalizedUpdaterBlinded extends FinalizedUpdaterCommon {

    void addFinalizedBlockRootBySlot(final UInt64 slot, final Bytes32 root);

    void addBlindedFinalizedBlock(
        final SignedBeaconBlock block, final Bytes32 root, final Spec spec);

    void addBlindedFinalizedBlockRaw(Bytes blockBytes, Bytes32 root, UInt64 slot);

    void addBlindedBlock(final SignedBeaconBlock block, final Bytes32 blockRoot, final Spec spec);

    void addExecutionPayload(final Bytes32 blockRoot, final ExecutionPayload payload);

    void deleteBlindedBlock(final Bytes32 root);

    void deleteExecutionPayload(final Bytes32 blockRoot);

    void pruneFinalizedBlindedBlocks(UInt64 firstSlotToPrune, UInt64 lastSlotToPrune);
  }
}

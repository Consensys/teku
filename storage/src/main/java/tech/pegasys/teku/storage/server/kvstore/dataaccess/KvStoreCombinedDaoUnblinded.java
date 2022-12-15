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
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

public interface KvStoreCombinedDaoUnblinded extends KvStoreCombinedDaoCommon {

  @MustBeClosed
  HotUpdaterUnblinded hotUpdaterUnblinded();

  @MustBeClosed
  FinalizedUpdaterUnblinded finalizedUpdaterUnblinded();

  @MustBeClosed
  CombinedUpdaterUnblinded combinedUpdaterUnblinded();

  Optional<SignedBeaconBlock> getHotBlock(Bytes32 root);

  @MustBeClosed
  Stream<SignedBeaconBlock> streamHotBlocks();

  Stream<Map.Entry<Bytes, Bytes>> streamUnblindedHotBlocksAsSsz();

  long countUnblindedHotBlocks();

  Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root);

  Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(UInt64 slot);

  Optional<UInt64> getEarliestFinalizedBlockSlot();

  Optional<SignedBeaconBlock> getEarliestFinalizedBlock();

  Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(UInt64 slot);

  List<SignedBeaconBlock> getNonCanonicalUnblindedBlocksAtSlot(UInt64 slot);

  @MustBeClosed
  Stream<SignedBeaconBlock> streamUnblindedFinalizedBlocks(UInt64 startSlot, UInt64 endSlot);

  @MustBeClosed
  Stream<Map.Entry<Bytes, Bytes>> streamUnblindedFinalizedBlocksRaw();

  Optional<UInt64> getSlotForFinalizedBlockRoot(Bytes32 blockRoot);

  Optional<UInt64> getSlotForFinalizedStateRoot(Bytes32 stateRoot);

  Optional<? extends SignedBeaconBlock> getNonCanonicalBlock(Bytes32 root);

  @MustBeClosed
  Stream<SlotAndBlockRoot> streamFinalizedBlockSlotAndRoots(UInt64 start, UInt64 end);

  interface CombinedUpdaterUnblinded
      extends HotUpdaterUnblinded, FinalizedUpdaterUnblinded, CombinedUpdaterCommon {}

  interface HotUpdaterUnblinded extends HotUpdaterCommon {
    void addHotBlock(BlockAndCheckpoints blockAndCheckpointEpochs);

    default void addHotBlocks(final Map<Bytes32, BlockAndCheckpoints> blocks) {
      blocks.values().forEach(this::addHotBlock);
    }

    void deleteHotBlock(Bytes32 blockRoot);

    void deleteUnblindedHotBlockOnly(Bytes32 blockRoot);
  }

  interface FinalizedUpdaterUnblinded extends FinalizedUpdaterCommon {

    void addFinalizedBlock(final SignedBeaconBlock block);

    void addNonCanonicalBlock(final SignedBeaconBlock block);

    void deleteUnblindedFinalizedBlock(final UInt64 slot, final Bytes32 blockRoot);

    void deleteUnblindedNonCanonicalBlockOnly(final Bytes32 blockRoot);

    void pruneFinalizedUnblindedBlocks(UInt64 firstSlotToPrune, UInt64 lastSlotToPrune);
  }
}

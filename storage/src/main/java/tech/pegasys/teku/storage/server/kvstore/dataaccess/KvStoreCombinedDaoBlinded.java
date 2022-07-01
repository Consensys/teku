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
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.CheckpointEpochs;
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

  long countBlindedBlocks();

  Optional<SignedBeaconBlock> getBlindedBlock(Bytes32 root);

  @MustBeClosed
  Stream<SignedBeaconBlock> streamBlindedHotBlocks();

  Optional<Bytes> getExecutionPayload(Bytes32 root);

  Optional<UInt64> getEarliestBlindedBlockSlot();

  Optional<SignedBeaconBlock> getEarliestBlindedBlock();

  Optional<SignedBeaconBlock> getLatestBlindedBlockAtSlot(UInt64 slot);

  Optional<Bytes32> getFinalizedBlockRootAtSlot(UInt64 slot);

  Optional<? extends SignedBeaconBlock> getNonCanonicalBlock(Bytes32 root);

  @MustBeClosed
  Stream<Bytes32> streamFinalizedBlockRoots(UInt64 startSlot, UInt64 endSlot);

  interface CombinedUpdaterBlinded
      extends HotUpdaterBlinded, FinalizedUpdaterBlinded, CombinedUpdaterCommon {}

  interface HotUpdaterBlinded extends HotUpdaterCommon {
    void addHotBlockCheckpointEpochs(Bytes32 blockRoot, CheckpointEpochs checkpointEpochs);

    default void addCheckpointEpochs(final Map<Bytes32, BlockAndCheckpointEpochs> blocks) {
      blocks.forEach((k, v) -> addHotBlockCheckpointEpochs(k, v.getCheckpointEpochs()));
    }

    void pruneHotBlockContext(Bytes32 blockRoot);
  }

  interface FinalizedUpdaterBlinded extends FinalizedUpdaterCommon {

    void addFinalizedBlockRootBySlot(final SignedBeaconBlock block);

    void addBlindedBlock(final SignedBeaconBlock block, final Spec spec);

    void addExecutionPayload(final ExecutionPayload payload);

    void deleteBlindedBlock(final Bytes32 root);

    void deleteExecutionPayload(final Bytes32 payloadHash);
  }
}

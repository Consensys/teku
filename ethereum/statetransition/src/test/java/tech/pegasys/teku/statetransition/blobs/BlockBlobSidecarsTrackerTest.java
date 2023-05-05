/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.statetransition.blobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockBlobSidecarsTrackerTest {
  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final UInt64 maxBlobsPerBlock = UInt64.valueOf(spec.getMaxBlobsPerBlock().orElseThrow());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final SignedBeaconBlock block =
      dataStructureUtil.randomSignedBeaconBlockWithCommitments(4);
  private final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();
  private final List<BlobSidecar> blobSidecarsForBlock =
      dataStructureUtil.randomBlobSidecarsForBlock(block);
  private final List<BlobIdentifier> blobIdentifiersForBlock =
      blobSidecarsForBlock.stream()
          .map(
              blobSidecar -> new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex()))
          .collect(Collectors.toList());

  @Test
  void isNotCompletedJustAfterCreation() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock);

    SafeFutureAssert.assertThatSafeFuture(blockBlobSidecarsTracker.getCompletionFuture())
        .isNotCompleted();
    assertThat(blockBlobSidecarsTracker.getMissingBlobSidecars()).isEmpty();
    assertThat(blockBlobSidecarsTracker.getBlockBody()).isEmpty();
    assertThat(blockBlobSidecarsTracker.getBlobSidecars()).isEmpty();
    assertThat(blockBlobSidecarsTracker.getSlotAndBlockRoot()).isEqualTo(slotAndBlockRoot);
    assertThat(
            blockBlobSidecarsTracker.containsBlobSidecar(dataStructureUtil.randomBlobIdentifier()))
        .isFalse();
  }

  @Test
  void setBlock_shouldAcceptCorrectBlock() {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock);
    blockBlobSidecarsTracker.setBlock(block);

    SafeFutureAssert.assertThatSafeFuture(blockBlobSidecarsTracker.getCompletionFuture())
        .isNotCompleted();
    assertThat(blockBlobSidecarsTracker.getMissingBlobSidecars())
        .containsExactlyInAnyOrderElementsOf(blobIdentifiersForBlock);
    assertThat(blockBlobSidecarsTracker.getBlockBody())
        .isEqualTo(Optional.of(block.getMessage().getBody()));
    assertThat(blockBlobSidecarsTracker.getBlobSidecars()).isEmpty();
  }

  @Test
  void setBlock_shouldThrowWithWrongBlock() {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(dataStructureUtil.randomSlotAndBlockRoot(), maxBlobsPerBlock);
    assertThatThrownBy(() -> blockBlobSidecarsTracker.setBlock(block))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void setBlock_shouldAcceptBlockTwice() {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock);
    blockBlobSidecarsTracker.setBlock(block);
    blockBlobSidecarsTracker.setBlock(block);
    assertThat(blockBlobSidecarsTracker.getBlockBody())
        .isEqualTo(Optional.of(block.getMessage().getBody()));
  }

  @Test
  void setBlock_immediatelyCompletesWithBlockWithoutBlobs() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithEmptyCommitments();
    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();

    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock);
    final SafeFuture<Void> completionFuture = blockBlobSidecarsTracker.getCompletionFuture();

    SafeFutureAssert.assertThatSafeFuture(completionFuture).isNotCompleted();

    blockBlobSidecarsTracker.setBlock(block);

    SafeFutureAssert.assertThatSafeFuture(completionFuture).isCompleted();
    assertThat(blockBlobSidecarsTracker.isCompleted()).isTrue();

    assertThat(blockBlobSidecarsTracker.getMissingBlobSidecars()).isEmpty();
    assertThat(blockBlobSidecarsTracker.getBlobSidecars()).isEmpty();
  }

  @Test
  void add_shouldWorkTillCompletionWhenAddingBlobsBeforeBlockIsSet() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock.plus(1));
    final BlobSidecar toAdd = blobSidecarsForBlock.get(0);
    final Map<UInt64, BlobSidecar> added = new HashMap<>();
    final SafeFuture<Void> completionFuture = blockBlobSidecarsTracker.getCompletionFuture();

    added.put(toAdd.getIndex(), toAdd);
    blockBlobSidecarsTracker.add(toAdd);

    // we don't know the block, missing blobs are max blobs minus the blob we already have
    final Set<BlobIdentifier> potentialMissingBlocks =
        UInt64.range(UInt64.valueOf(1), UInt64.valueOf(5))
            .map(index -> new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), index))
            .collect(Collectors.toSet());

    SafeFutureAssert.assertThatSafeFuture(completionFuture).isNotCompleted();
    assertThat(blockBlobSidecarsTracker.getMissingBlobSidecars())
        .containsExactlyInAnyOrderElementsOf(potentialMissingBlocks);
    assertThat(blockBlobSidecarsTracker.getBlobSidecars())
        .containsExactlyInAnyOrderEntriesOf(added);

    blockBlobSidecarsTracker.setBlock(block);
    assertThat(blockBlobSidecarsTracker.getBlockBody())
        .isEqualTo(Optional.of(block.getMessage().getBody()));

    // now we know the block and we know about missing blobs
    final List<BlobIdentifier> stillMissing =
        blobIdentifiersForBlock.subList(1, blobIdentifiersForBlock.size());
    assertThat(blockBlobSidecarsTracker.getMissingBlobSidecars())
        .containsExactlyInAnyOrderElementsOf(stillMissing);
    SafeFutureAssert.assertThatSafeFuture(completionFuture).isNotCompleted();

    // let's complete with missing blobs
    for (int idx = 1; idx < blobIdentifiersForBlock.size(); idx++) {

      BlobSidecar blobSidecar = blobSidecarsForBlock.get(idx);

      blockBlobSidecarsTracker.add(blobSidecar);
      added.put(blobSidecar.getIndex(), blobSidecar);
      assertThat(blockBlobSidecarsTracker.getBlobSidecars())
          .containsExactlyInAnyOrderEntriesOf(added);

      if (idx == blobIdentifiersForBlock.size() - 1) {
        SafeFutureAssert.assertThatSafeFuture(completionFuture).isCompleted();
        assertThat(blockBlobSidecarsTracker.isCompleted()).isTrue();
      } else {
        SafeFutureAssert.assertThatSafeFuture(completionFuture).isNotCompleted();
        assertThat(blockBlobSidecarsTracker.isCompleted()).isFalse();
      }
    }

    SafeFutureAssert.assertThatSafeFuture(completionFuture).isCompleted();
    assertThat(blockBlobSidecarsTracker.isCompleted()).isTrue();
  }

  @Test
  void add_shouldWorkWhenBlockIsSetFirst() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock);
    final SafeFuture<Void> completionFuture = blockBlobSidecarsTracker.getCompletionFuture();

    blockBlobSidecarsTracker.setBlock(block);

    final BlobSidecar toAdd = blobSidecarsForBlock.get(0);
    final Map<UInt64, BlobSidecar> added = new HashMap<>();

    final List<BlobIdentifier> stillMissing =
        blobIdentifiersForBlock.subList(1, blobIdentifiersForBlock.size());

    added.put(toAdd.getIndex(), toAdd);
    blockBlobSidecarsTracker.add(toAdd);

    assertThat(blockBlobSidecarsTracker.getMissingBlobSidecars())
        .containsExactlyInAnyOrderElementsOf(stillMissing);
    SafeFutureAssert.assertThatSafeFuture(completionFuture).isNotCompleted();
    assertThat(blockBlobSidecarsTracker.getBlobSidecars())
        .containsExactlyInAnyOrderEntriesOf(added);
    assertThat(blockBlobSidecarsTracker.getBlockBody())
        .isEqualTo(Optional.of(block.getMessage().getBody()));
  }

  @Test
  void add_shouldThrowWhenAddingInconsistentBlobSidecar() {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(dataStructureUtil.randomSlotAndBlockRoot(), maxBlobsPerBlock);
    assertThatThrownBy(() -> blockBlobSidecarsTracker.add(dataStructureUtil.randomBlobSidecar()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void add_shouldAcceptAcceptSameBlobSidecarTwice() {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock);
    blockBlobSidecarsTracker.setBlock(block);
    blockBlobSidecarsTracker.setBlock(block);
    assertThat(blockBlobSidecarsTracker.getBlockBody())
        .isEqualTo(Optional.of(block.getMessage().getBody()));
  }

  @Test
  void getMissingBlobSidecars_shouldReturnPartialBlobsIdentifierWhenBlockIsUnknown() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock);
    final BlobSidecar toAdd = blobSidecarsForBlock.get(2);

    blockBlobSidecarsTracker.add(toAdd);

    final List<BlobIdentifier> knownMissing =
        blobIdentifiersForBlock.stream()
            .filter(blobIdentifier -> !blobIdentifier.getIndex().equals(UInt64.valueOf(2)))
            .collect(Collectors.toList());

    assertThat(blockBlobSidecarsTracker.getMissingBlobSidecars())
        .containsExactlyInAnyOrderElementsOf(knownMissing);
  }

  @Test
  void getUnusedBlobSidecarsForBlock_shouldReturnShouldFailIfBlockIsUnknown() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock);

    assertThatThrownBy(blockBlobSidecarsTracker::getUnusedBlobSidecarsForBlock)
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getUnusedBlobSidecarsForBlock_shouldReturnEmptySetIfBlockIsFull() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock);

    blockBlobSidecarsTracker.setBlock(block);

    assertThat(blockBlobSidecarsTracker.getUnusedBlobSidecarsForBlock()).isEmpty();
  }

  @Test
  void getUnusedBlobSidecarsForBlock_shouldReturnUnusedBlobSpace() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock.plus(2));

    blockBlobSidecarsTracker.setBlock(block);

    final Set<BlobIdentifier> expectedUnusedBlobs =
        UInt64.range(UInt64.valueOf(4), UInt64.valueOf(6))
            .map(index -> new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), index))
            .collect(Collectors.toSet());

    assertThat(blockBlobSidecarsTracker.getUnusedBlobSidecarsForBlock())
        .containsExactlyInAnyOrderElementsOf(expectedUnusedBlobs);
  }

  @Test
  void getUnusedBlobSidecarsForBlock_shouldReturnAllMaxBlobsPerBlockIfBlockIsEmpty() {

    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithEmptyCommitments();
    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();

    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock.plus(2));

    blockBlobSidecarsTracker.setBlock(block);

    final Set<BlobIdentifier> expectedUnusedBlobs =
        UInt64.range(UInt64.ZERO, UInt64.valueOf(6))
            .map(index -> new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), index))
            .collect(Collectors.toSet());

    assertThat(blockBlobSidecarsTracker.getUnusedBlobSidecarsForBlock())
        .containsExactlyInAnyOrderElementsOf(expectedUnusedBlobs);
  }

  @Test
  void getMissingBlobSidecars_shouldRespectMaxBlobsPerBlock() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot, maxBlobsPerBlock);
    final BlobSidecar toAdd =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .blockRoot(slotAndBlockRoot.getBlockRoot())
            .slot(slotAndBlockRoot.getSlot())
            .index(UInt64.valueOf(100))
            .build();

    blockBlobSidecarsTracker.add(toAdd);

    final List<BlobIdentifier> knownMissing =
        blobIdentifiersForBlock.subList(0, maxBlobsPerBlock.intValue());

    assertThat(blockBlobSidecarsTracker.getMissingBlobSidecars())
        .containsExactlyInAnyOrderElementsOf(knownMissing);
  }
}

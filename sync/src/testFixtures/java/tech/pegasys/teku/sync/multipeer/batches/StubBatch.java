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

package tech.pegasys.teku.sync.multipeer.batches;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Arrays.asList;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.sync.multipeer.BatchImporter.BatchImportResult;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;

public class StubBatch implements Batch {
  private final TargetChain targetChain;
  private final UInt64 firstSlot;
  private final UInt64 count;
  private final SafeFuture<BatchImportResult> importResult = new SafeFuture<>();
  private final List<SignedBeaconBlock> blocks = new ArrayList<>();
  private boolean invalid = false;
  private Optional<Runnable> blockCallback = Optional.empty();
  private boolean complete = false;
  private boolean contested = false;
  private boolean firstBlockConfirmed = false;
  private boolean lastBlockCorrect = false;

  public StubBatch(final TargetChain targetChain, final UInt64 firstSlot, final UInt64 count) {
    this.targetChain = targetChain;
    this.firstSlot = firstSlot;
    this.count = count;
  }

  public void receiveBlocksAndMarkComplete(final SignedBeaconBlock... blocks) {
    receiveBlocks(blocks, true);
  }

  public void receiveBlocks(final SignedBeaconBlock... blocks) {
    // Batches automatically mark themselves complete when the block is received for the last slot
    // or a request comes back completely empty
    final boolean markComplete =
        blocks.length == 0 || blocks[blocks.length - 1].getSlot().equals(getLastSlot());
    receiveBlocks(blocks, markComplete);
  }

  private void receiveBlocks(final SignedBeaconBlock[] blocks, final boolean markComplete) {
    checkState(blockCallback.isPresent(), "Received blocks when none were expected");
    this.blocks.addAll(asList(blocks));
    if (markComplete) {
      complete = true;
    }
    final Runnable callback = blockCallback.get();
    blockCallback = Optional.empty();
    callback.run();
  }

  @Override
  public UInt64 getFirstSlot() {
    return firstSlot;
  }

  @Override
  public UInt64 getLastSlot() {
    return firstSlot.plus(count);
  }

  @Override
  public Optional<SignedBeaconBlock> getFirstBlock() {
    return blocks.isEmpty() ? Optional.empty() : Optional.of(blocks.get(0));
  }

  @Override
  public Optional<SignedBeaconBlock> getLastBlock() {
    return blocks.isEmpty() ? Optional.empty() : Optional.of(blocks.get(blocks.size() - 1));
  }

  @Override
  public List<SignedBeaconBlock> getBlocks() {
    return blocks;
  }

  @Override
  public void markComplete() {
    complete = true;
  }

  @Override
  public boolean isComplete() {
    return complete;
  }

  @Override
  public boolean isConfirmed() {
    return firstBlockConfirmed && lastBlockCorrect;
  }

  @Override
  public boolean isFirstBlockConfirmed() {
    return firstBlockConfirmed;
  }

  @Override
  public boolean isContested() {
    return contested;
  }

  @Override
  public void markFirstBlockConfirmed() {
    firstBlockConfirmed = true;
  }

  @Override
  public void markLastBlockConfirmed() {
    lastBlockCorrect = true;
  }

  @Override
  public void markAsContested() {
    // TODO: Be more careful about exact behaviour here
    contested = true;
    complete = false;
    blocks.clear();
  }

  @Override
  public boolean isEmpty() {
    return blocks.isEmpty();
  }

  @Override
  public boolean isAwaitingBlocks() {
    return blockCallback.isPresent();
  }

  @Override
  public void requestMoreBlocks(final Runnable callback) {
    blockCallback = Optional.of(callback);
  }

  @Override
  public TargetChain getTargetChain() {
    return targetChain;
  }

  @Override
  public void markAsInvalid() {
    invalid = true;
  }

  // Real batch would just reset and select a different peer to retrieve from
  // but we want to know it was marked invalid for tests
  public boolean isInvalid() {
    return invalid;
  }

  public SafeFuture<BatchImportResult> getImportResult() {
    return importResult;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("firstSlot", firstSlot)
        .add("count", count)
        .add("invalid", invalid)
        .add("complete", complete)
        .add("firstBlockCorrect", firstBlockConfirmed)
        .add("lastBlockCorrect", lastBlockCorrect)
        .toString();
  }
}

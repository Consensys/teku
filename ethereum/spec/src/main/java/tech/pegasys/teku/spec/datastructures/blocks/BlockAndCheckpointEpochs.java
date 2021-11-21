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

package tech.pegasys.teku.spec.datastructures.blocks;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;

public class BlockAndCheckpointEpochs implements BeaconBlockSummary {
  private final SignedBeaconBlock block;
  private final CheckpointEpochs checkpointEpochs;

  public BlockAndCheckpointEpochs(
      final SignedBeaconBlock block, final CheckpointEpochs checkpointEpochs) {
    this.block = block;
    this.checkpointEpochs = checkpointEpochs;
  }

  public static BlockAndCheckpointEpochs fromBlockAndState(
      final SignedBlockAndState blockAndState) {
    return new BlockAndCheckpointEpochs(
        blockAndState.getBlock(), CheckpointEpochs.fromBlockAndState(blockAndState));
  }

  public SignedBeaconBlock getBlock() {
    return block;
  }

  public CheckpointEpochs getCheckpointEpochs() {
    return checkpointEpochs;
  }

  @Override
  public UInt64 getSlot() {
    return block.getSlot();
  }

  @Override
  public UInt64 getProposerIndex() {
    return block.getProposerIndex();
  }

  @Override
  public Bytes32 getParentRoot() {
    return block.getParentRoot();
  }

  @Override
  public Bytes32 getStateRoot() {
    return block.getStateRoot();
  }

  @Override
  public Bytes32 getBodyRoot() {
    return block.getBodyRoot();
  }

  @Override
  public Bytes32 getRoot() {
    return block.getRoot();
  }

  @Override
  public Optional<BeaconBlock> getBeaconBlock() {
    return Optional.of(block.getMessage());
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBeaconBlock() {
    return Optional.of(block);
  }

  public Optional<Bytes32> getExecutionBlockHash() {
    return block
        .getMessage()
        .getBody()
        .getOptionalExecutionPayload()
        .map(ExecutionPayload::getBlockHash);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BlockAndCheckpointEpochs that = (BlockAndCheckpointEpochs) o;
    return Objects.equals(block, that.block)
        && Objects.equals(checkpointEpochs, that.checkpointEpochs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(block, checkpointEpochs);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("block", block)
        .add("checkpointEpochs", checkpointEpochs)
        .toString();
  }
}

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

package tech.pegasys.teku.storage.api;

import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;

public class StoredBlockMetadata {
  private final UInt64 blockSlot;
  private final Bytes32 blockRoot;
  private final Bytes32 parentRoot;
  private final Bytes32 stateRoot;
  private final Optional<Bytes32> executionBlockHash;
  private final Optional<BlockCheckpoints> checkpointEpochs;

  public StoredBlockMetadata(
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final Optional<Bytes32> executionBlockHash,
      final Optional<BlockCheckpoints> checkpointEpochs) {
    this.blockSlot = blockSlot;
    this.blockRoot = blockRoot;
    this.parentRoot = parentRoot;
    this.stateRoot = stateRoot;
    this.executionBlockHash = executionBlockHash;
    this.checkpointEpochs = checkpointEpochs;
  }

  public static StoredBlockMetadata fromBlockAndCheckpointEpochs(
      final SignedBeaconBlock block, final BlockCheckpoints checkpointEpochs) {
    return new StoredBlockMetadata(
        block.getSlot(),
        block.getRoot(),
        block.getParentRoot(),
        block.getStateRoot(),
        block
            .getMessage()
            .getBody()
            .getOptionalExecutionPayloadSummary()
            .map(ExecutionPayloadSummary::getBlockHash),
        Optional.of(checkpointEpochs));
  }

  public static StoredBlockMetadata fromBlockAndState(
      final Spec spec, final StateAndBlockSummary blockAndState) {
    final BlockCheckpoints epochs = spec.calculateBlockCheckpoints(blockAndState.getState());
    return new StoredBlockMetadata(
        blockAndState.getSlot(),
        blockAndState.getRoot(),
        blockAndState.getParentRoot(),
        blockAndState.getStateRoot(),
        blockAndState.getExecutionBlockHash(),
        Optional.of(epochs));
  }

  public UInt64 getBlockSlot() {
    return blockSlot;
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public Bytes32 getParentRoot() {
    return parentRoot;
  }

  public Bytes32 getStateRoot() {
    return stateRoot;
  }

  public Optional<Bytes32> getExecutionBlockHash() {
    return executionBlockHash;
  }

  public Optional<BlockCheckpoints> getCheckpointEpochs() {
    return checkpointEpochs;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final StoredBlockMetadata that = (StoredBlockMetadata) o;
    return Objects.equals(blockSlot, that.blockSlot)
        && Objects.equals(blockRoot, that.blockRoot)
        && Objects.equals(parentRoot, that.parentRoot)
        && Objects.equals(stateRoot, that.stateRoot)
        && Objects.equals(checkpointEpochs, that.checkpointEpochs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(blockSlot, blockRoot, parentRoot, stateRoot, checkpointEpochs);
  }
}

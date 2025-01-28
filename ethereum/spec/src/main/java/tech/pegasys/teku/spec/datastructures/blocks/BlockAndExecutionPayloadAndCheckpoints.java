/*
 * Copyright Consensys Software Inc., 2022
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
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;

public class BlockAndExecutionPayloadAndCheckpoints {
  private final SignedBeaconBlock block;
  private final SignedExecutionPayloadEnvelope executionPayload;
  private final BlockCheckpoints blockCheckpoints;

  public BlockAndExecutionPayloadAndCheckpoints(
      final SignedBeaconBlock block,
      final SignedExecutionPayloadEnvelope executionPayload,
      final BlockCheckpoints blockCheckpoints) {
    this.block = block;
    this.executionPayload = executionPayload;
    this.blockCheckpoints = blockCheckpoints;
  }

  public SignedBeaconBlock getBlock() {
    return block;
  }

  public SignedExecutionPayloadEnvelope getExecutionPayload() {
    return executionPayload;
  }

  public BlockCheckpoints getBlockCheckpoints() {
    return blockCheckpoints;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BlockAndExecutionPayloadAndCheckpoints that = (BlockAndExecutionPayloadAndCheckpoints) o;
    return Objects.equals(block, that.block)
        && Objects.equals(executionPayload, that.executionPayload)
        && Objects.equals(blockCheckpoints, that.blockCheckpoints);
  }

  @Override
  public int hashCode() {
    return Objects.hash(block, blockCheckpoints);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("block", block)
        .add("executionPayload", executionPayload)
        .add("blockCheckpoints", blockCheckpoints)
        .toString();
  }
}

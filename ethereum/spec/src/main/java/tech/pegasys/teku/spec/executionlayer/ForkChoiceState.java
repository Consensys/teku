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

package tech.pegasys.teku.spec.executionlayer;

import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ForkChoiceState {
  private final Bytes32 headBlockRoot;
  private final UInt64 headBlockSlot;

  private final Bytes32 headExecutionBlockHash;
  private final Bytes32 safeExecutionBlockHash;
  private final Bytes32 finalizedExecutionBlockHash;
  private final boolean isHeadOptimistic;
  private final Optional<UInt64> genesisTime;

  public ForkChoiceState(
      final Bytes32 headBlockRoot,
      final UInt64 headBlockSlot,
      final Bytes32 headExecutionBlockHash,
      final Bytes32 safeExecutionBlockHash,
      final Bytes32 finalizedExecutionBlockHash,
      final boolean isHeadOptimistic,
      final Optional<UInt64> genesisTime) {
    this.headBlockRoot = headBlockRoot;
    this.headBlockSlot = headBlockSlot;
    this.headExecutionBlockHash = headExecutionBlockHash;
    this.safeExecutionBlockHash = safeExecutionBlockHash;
    this.finalizedExecutionBlockHash = finalizedExecutionBlockHash;
    this.isHeadOptimistic = isHeadOptimistic;
    this.genesisTime = genesisTime;
  }

  public Bytes32 getHeadBlockRoot() {
    return headBlockRoot;
  }

  public UInt64 getHeadBlockSlot() {
    return headBlockSlot;
  }

  public Bytes32 getHeadExecutionBlockHash() {
    return headExecutionBlockHash;
  }

  public Bytes32 getSafeExecutionBlockHash() {
    return safeExecutionBlockHash;
  }

  public Bytes32 getFinalizedExecutionBlockHash() {
    return finalizedExecutionBlockHash;
  }

  public boolean isHeadOptimistic() {
    return isHeadOptimistic;
  }

  public Optional<UInt64> getGenesisTime() {
    return genesisTime;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("ForkChoiceState{");
    sb.append("headBlockRoot=").append(headBlockRoot);
    sb.append(", headBlockSlot=").append(headBlockSlot);
    sb.append(", headExecutionBlockHash=").append(headExecutionBlockHash);
    sb.append(", safeExecutionBlockHash=").append(safeExecutionBlockHash);
    sb.append(", finalizedExecutionBlockHash=").append(finalizedExecutionBlockHash);
    sb.append(", isHeadOptimistic=").append(isHeadOptimistic);
    sb.append(", genesisTime=").append(genesisTime);
    sb.append('}');
    return sb.toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ForkChoiceState that = (ForkChoiceState) o;
    return isHeadOptimistic == that.isHeadOptimistic
        && Objects.equals(headBlockRoot, that.headBlockRoot)
        && Objects.equals(headBlockSlot, that.headBlockSlot)
        && Objects.equals(headExecutionBlockHash, that.headExecutionBlockHash)
        && Objects.equals(safeExecutionBlockHash, that.safeExecutionBlockHash)
        && Objects.equals(finalizedExecutionBlockHash, that.finalizedExecutionBlockHash)
        && Objects.equals(genesisTime, that.genesisTime);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        headBlockRoot,
        headBlockSlot,
        headExecutionBlockHash,
        safeExecutionBlockHash,
        finalizedExecutionBlockHash,
        isHeadOptimistic,
        genesisTime);
  }
}

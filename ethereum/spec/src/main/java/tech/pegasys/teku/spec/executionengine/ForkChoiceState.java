/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.executionengine;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;

public class ForkChoiceState {
  private final Bytes32 headBlockHash;
  private final Bytes32 safeBlockHash;
  private final Bytes32 finalizedBlockHash;

  public static Optional<ForkChoiceState> fromHeadAndFinalized(
      Optional<Bytes32> maybeHeadBlock, Bytes32 finalizedBlock) {
    return maybeHeadBlock.map(
        headBlock -> new ForkChoiceState(headBlock, headBlock, finalizedBlock));
  }

  public ForkChoiceState(Bytes32 headBlockHash, Bytes32 safeBlockHash, Bytes32 finalizedBlockHash) {
    this.headBlockHash = headBlockHash;
    this.safeBlockHash = safeBlockHash;
    this.finalizedBlockHash = finalizedBlockHash;
  }

  public Bytes32 getHeadBlockHash() {
    return headBlockHash;
  }

  public Bytes32 getSafeBlockHash() {
    return safeBlockHash;
  }

  public Bytes32 getFinalizedBlockHash() {
    return finalizedBlockHash;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("headBlockHash", headBlockHash)
        .add("safeBlockHash", safeBlockHash)
        .add("finalizedBlockHash", finalizedBlockHash)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ForkChoiceState)) return false;
    ForkChoiceState that = (ForkChoiceState) o;
    return headBlockHash == that.headBlockHash
        && safeBlockHash == that.safeBlockHash
        && finalizedBlockHash == that.finalizedBlockHash;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(headBlockHash, safeBlockHash, finalizedBlockHash);
  }
}

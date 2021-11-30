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
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;

public class ForkChoiceState {
  private final Bytes32 headBlockHash;
  private final Bytes32 safeBlockHash;
  private final Bytes32 finalizedBlockHash;

  public ForkChoiceState(
      final Bytes32 headBlockHash, final Bytes32 safeBlockHash, final Bytes32 finalizedBlockHash) {
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
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ForkChoiceState that = (ForkChoiceState) o;
    return Objects.equals(headBlockHash, that.headBlockHash)
        && Objects.equals(safeBlockHash, that.safeBlockHash)
        && Objects.equals(finalizedBlockHash, that.finalizedBlockHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(headBlockHash, safeBlockHash, finalizedBlockHash);
  }
}

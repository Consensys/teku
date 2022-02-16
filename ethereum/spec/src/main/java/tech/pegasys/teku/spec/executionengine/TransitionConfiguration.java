/*
 * Copyright 2022 ConsenSys AG.
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
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class TransitionConfiguration {
  private final UInt256 terminalTotalDifficulty;
  private final Bytes32 terminalBlockHash;
  private final UInt64 terminalBlockNumber;

  public TransitionConfiguration(
      final UInt256 terminalTotalDifficulty,
      final Bytes32 terminalBlockHash,
      final UInt64 terminalBlockNumber) {
    this.terminalTotalDifficulty = terminalTotalDifficulty;
    this.terminalBlockHash = terminalBlockHash;
    this.terminalBlockNumber = terminalBlockNumber;
  }

  public UInt256 getTerminalTotalDifficulty() {
    return terminalTotalDifficulty;
  }

  public Bytes32 getTerminalBlockHash() {
    return terminalBlockHash;
  }

  public UInt64 getTerminalBlockNumber() {
    return terminalBlockNumber;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TransitionConfiguration that = (TransitionConfiguration) o;
    return Objects.equals(terminalTotalDifficulty, that.terminalTotalDifficulty)
        && Objects.equals(terminalBlockHash, that.terminalBlockHash)
        && Objects.equals(terminalBlockNumber, that.terminalBlockNumber);
  }

  @Override
  public int hashCode() {
    return Objects.hash(terminalTotalDifficulty, terminalBlockHash, terminalBlockNumber);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("terminalTotalDifficulty", terminalTotalDifficulty)
        .add("terminalBlockHash", terminalBlockHash)
        .add("terminalBlockNumber", terminalBlockNumber)
        .toString();
  }
}

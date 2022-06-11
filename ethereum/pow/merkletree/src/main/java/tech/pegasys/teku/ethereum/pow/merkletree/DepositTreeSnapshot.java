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

package tech.pegasys.teku.ethereum.pow.merkletree;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;

public class DepositTreeSnapshot {

  private final List<Bytes32> finalized;
  private final long deposits;
  private final Bytes32 executionBlockHash;

  public DepositTreeSnapshot(
      final List<Bytes32> finalized, final long deposits, final Bytes32 executionBlockHash) {
    this.finalized = finalized;
    this.deposits = deposits;
    this.executionBlockHash = executionBlockHash;
  }

  public List<Bytes32> getFinalized() {
    return finalized;
  }

  public long getDeposits() {
    return deposits;
  }

  public Bytes32 getExecutionBlockHash() {
    return executionBlockHash;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DepositTreeSnapshot that = (DepositTreeSnapshot) o;
    return deposits == that.deposits
        && Objects.equals(finalized, that.finalized)
        && Objects.equals(executionBlockHash, that.executionBlockHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(finalized, deposits, executionBlockHash);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("finalized", finalized)
        .add("deposits", deposits)
        .add("executionBlockHash", executionBlockHash)
        .toString();
  }
}

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

package tech.pegasys.teku.api.migrated;

import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockRewardData {
  private UInt64 proposerIndex;
  private final long attestations;
  private final long syncAggregate;
  private final long proposerSlashings;
  private final long attesterSlashings;

  public BlockRewardData(
      final UInt64 proposerIndex,
      final long attestations,
      final long syncAggregate,
      final long proposerSlashings,
      final long attesterSlashings) {
    this.proposerIndex = proposerIndex;
    this.attestations = attestations;
    this.syncAggregate = syncAggregate;
    this.proposerSlashings = proposerSlashings;
    this.attesterSlashings = attesterSlashings;
  }

  public UInt64 getProposerIndex() {
    return proposerIndex;
  }

  public long getTotal() {
    return attestations + syncAggregate + proposerSlashings + attesterSlashings;
  }

  public long getAttestations() {
    return attestations;
  }

  public long getSyncAggregate() {
    return syncAggregate;
  }

  public long getProposerSlashings() {
    return proposerSlashings;
  }

  public long getAttesterSlashings() {
    return attesterSlashings;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlockRewardData that = (BlockRewardData) o;
    return attestations == that.attestations
        && syncAggregate == that.syncAggregate
        && proposerSlashings == that.proposerSlashings
        && attesterSlashings == that.attesterSlashings
        && Objects.equals(proposerIndex, that.proposerIndex);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        proposerIndex, attestations, syncAggregate, proposerSlashings, attesterSlashings);
  }

  @Override
  public String toString() {
    return "BlockRewardData{"
        + "proposerIndex="
        + proposerIndex
        + ", attestations="
        + attestations
        + ", syncAggregate="
        + syncAggregate
        + ", proposerSlashings="
        + proposerSlashings
        + ", attesterSlashings="
        + attesterSlashings
        + '}';
  }
}

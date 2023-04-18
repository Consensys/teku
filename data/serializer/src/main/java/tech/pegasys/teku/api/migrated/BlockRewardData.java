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

public class BlockRewardData {
  private final int proposerIndex;
  private final long total;
  private final long attestations;
  private final long syncAggregate;
  private final long proposerSlashings;
  private final long attesterSlashings;

  public BlockRewardData(
      final int proposerIndex,
      final long total,
      final long attestations,
      final long syncAggregate,
      final long proposerSlashings,
      final long attesterSlashings) {
    this.proposerIndex = proposerIndex;
    this.total = total;
    this.attestations = attestations;
    this.syncAggregate = syncAggregate;
    this.proposerSlashings = proposerSlashings;
    this.attesterSlashings = attesterSlashings;
  }

  public int getProposerIndex() {
    return proposerIndex;
  }

  public long getTotal() {
    return total;
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
    return Objects.equals(proposerIndex, that.proposerIndex)
        && Objects.equals(total, that.total)
        && Objects.equals(attestations, that.attestations)
        && Objects.equals(syncAggregate, that.syncAggregate)
        && Objects.equals(proposerSlashings, that.proposerSlashings)
        && Objects.equals(attesterSlashings, that.attesterSlashings);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        proposerIndex, total, attestations, syncAggregate, proposerSlashings, attesterSlashings);
  }

  @Override
  public String toString() {
    return "BlockRewardData{"
        + "proposerIndex="
        + proposerIndex
        + ", total="
        + total
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

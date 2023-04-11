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
  private final Integer proposerIndex;
  private final Long total;
  private final Long attestations;
  private final UInt64 syncAggregate;
  private final UInt64 proposerSlashings;
  private final UInt64 attesterSlashings;

  public BlockRewardData(
      final Integer proposerIndex,
      final Long total,
      final Long attestations,
      final UInt64 syncAggregate,
      final UInt64 proposerSlashings,
      final UInt64 attesterSlashings) {
    this.proposerIndex = proposerIndex;
    this.total = total;
    this.attestations = attestations;
    this.syncAggregate = syncAggregate;
    this.proposerSlashings = proposerSlashings;
    this.attesterSlashings = attesterSlashings;
  }

  public Integer getProposerIndex() {
    return proposerIndex;
  }

  public Long getTotal() {
    return total;
  }

  public Long getAttestations() {
    return attestations;
  }

  public UInt64 getSyncAggregate() {
    return syncAggregate;
  }

  public UInt64 getProposerSlashings() {
    return proposerSlashings;
  }

  public UInt64 getAttesterSlashings() {
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
}

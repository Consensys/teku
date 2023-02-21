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
  private final UInt64 proposerIndex;
  private final UInt64 total;
  private final UInt64 attestations;
  private final UInt64 syncAggregate;
  private final UInt64 proposerSlashings;
  private final UInt64 attesterSlashings;

  public BlockRewardData(
      final UInt64 proposerIndex,
      final UInt64 total,
      final UInt64 attestations,
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

  public UInt64 getProposerIndex() {
    return proposerIndex;
  }

  public UInt64 getTotal() {
    return total;
  }

  public UInt64 getAttestations() {
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

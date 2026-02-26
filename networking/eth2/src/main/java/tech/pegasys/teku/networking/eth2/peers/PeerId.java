/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.networking.eth2.peers;

import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public final class PeerId {
  private final NodeId existingId;
  private final Bytes candidateId;

  private PeerId(final NodeId existingId, final Bytes candidateId) {
    this.existingId = existingId;
    this.candidateId = candidateId;
  }

  public static PeerId fromExistingId(final NodeId id) {
    Objects.requireNonNull(id, "id must not be null");
    return new PeerId(id, null);
  }

  public static PeerId fromCandidateId(final Bytes id) {
    Objects.requireNonNull(id, "id must not be null");
    return new PeerId(null, id);
  }

  public boolean isExisting() {
    return existingId != null;
  }

  public boolean isCandidate() {
    return candidateId != null;
  }

  public Optional<NodeId> getExistingId() {
    return existingId == null ? Optional.empty() : Optional.of(existingId);
  }

  public Bytes getCandidateId() {
    return candidateId;
  }

  /** Returns canonical bytes representation for equality/hashCode */
  private Bytes asBytes() {
    return isExisting() ? existingId.toBytes() : candidateId;
  }

  /**
   * Returns the UInt256 representation of the PeerId for candidate peers, or empty if it's an
   * existing peer.
   */
  public Optional<UInt256> toUInt256() {
    return isExisting() ? Optional.empty() : Optional.of(UInt256.fromBytes(asBytes()));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PeerId other)) {
      return false;
    }
    return Objects.equals(this.asBytes(), other.asBytes());
  }

  @Override
  public int hashCode() {
    return Objects.hash(asBytes());
  }
}

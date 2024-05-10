/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.attestation.utils;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;

class AttestationBitsAggregatorPhase0 implements AttestationBitsAggregator {
  private SszBitlist aggregationBits;

  AttestationBitsAggregatorPhase0(final SszBitlist aggregationBits) {
    this.aggregationBits = aggregationBits;
  }

  static AttestationBitsAggregator fromAttestationSchema(AttestationSchema<?> attestationSchema) {
    return new AttestationBitsAggregatorPhase0(attestationSchema.createEmptyAggregationBits());
  }

  @Override
  public void aggregateWith(AttestationBitsAggregator other) {
    aggregationBits = aggregationBits.or(other.getAggregationBits());
  }

  @Override
  public void aggregateWith(Attestation other) {
    aggregationBits = aggregationBits.or(other.getAggregationBits());
  }

  @Override
  public boolean supersedes(Attestation other) {
    return aggregationBits.isSuperSetOf(other.getAggregationBits());
  }

  @Override
  public boolean canAggregateWith(Attestation other) {
    return !aggregationBits.intersects(other.getAggregationBits());
  }

  @Override
  public SszBitlist getAggregationBits() {
    return aggregationBits;
  }

  @Override
  public SszBitvector getCommitteeBits() {
    throw new IllegalStateException("Committee bits not available in phase0");
  }

  @Override
  public Int2IntMap getCommitteesSize() {
    throw new IllegalStateException("Committee sizes not available in phase0");
  }

  @Override
  public boolean requiresCommitteeBits() {
    return false;
  }
}

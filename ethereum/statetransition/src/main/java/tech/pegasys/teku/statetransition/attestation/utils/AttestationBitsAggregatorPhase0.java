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

package tech.pegasys.teku.statetransition.attestation.utils;

import com.google.common.base.MoreObjects;
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

  static AttestationBitsAggregator fromAttestationSchema(
      final AttestationSchema<?> attestationSchema) {
    return new AttestationBitsAggregatorPhase0(attestationSchema.createEmptyAggregationBits());
  }

  @Override
  public void or(final AttestationBitsAggregator other) {
    aggregationBits = aggregationBits.or(other.getAggregationBits());
  }

  @Override
  public boolean aggregateWith(final Attestation other) {
    return aggregateWith(other.getAggregationBits());
  }

  private boolean aggregateWith(final SszBitlist otherAggregationBits) {
    if (aggregationBits.intersects(otherAggregationBits)) {
      return false;
    }
    aggregationBits = aggregationBits.or(otherAggregationBits);
    return true;
  }

  @Override
  public void or(final Attestation other) {
    aggregationBits = aggregationBits.or(other.getAggregationBits());
  }

  @Override
  public boolean isSuperSetOf(final Attestation other) {
    return aggregationBits.isSuperSetOf(other.getAggregationBits());
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

  @Override
  public AttestationBitsAggregator copy() {
    return new AttestationBitsAggregatorPhase0(aggregationBits);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("aggregationBits", aggregationBits).toString();
  }
}

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
import it.unimi.dsi.fastutil.ints.IntList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;

@SuppressWarnings("FieldCanBeFinal")
class AttestationBitsCalculatorElectra implements AttestationBitsCalculator {
  private SszBitlist aggregationBits;
  private SszBitvector committeeBits;
  private Int2IntMap committeesSize;

  AttestationBitsCalculatorElectra(
      final SszBitlist aggregationBits,
      final SszBitvector committeeBits,
      Int2IntMap committeesSize) {
    this.aggregationBits = aggregationBits;
    this.committeeBits = committeeBits;
    this.committeesSize = committeesSize;
  }

  static AttestationBitsCalculator fromAttestationSchema(
      AttestationSchema<?> attestationSchema, Int2IntMap committeesSize) {
    return new AttestationBitsCalculatorElectra(
        attestationSchema.createEmptyAggregationBits(),
        attestationSchema.createEmptyCommitteeBits().orElseThrow(),
        committeesSize);
  }

  @Override
  public void aggregateWith(AttestationBitsCalculator other) {
    aggregationBits = aggregationBits.or(other.getAggregationBits());
  }

  @Override
  public void aggregateWith(Attestation other) {
    // assumes can aggregate refuses non-overlapping committees
    aggregationBits = aggregationBits.or(other.getAggregationBits());
  }

  @Override
  public boolean supersedes(Attestation other) {
    // TODO: we currently able to compare attestations with same committee bits.

    if (committeeBits.equals(other.getCommitteeBitsRequired())) {
      return aggregationBits.isSuperSetOf(other.getAggregationBits());
    }

    // we default to false, so we won't discard any potential attestations
    return false;
  }

  @Override
  public boolean canAggregateWith(Attestation other) {
    // TODO support aggregation of overlapping committees

    // this is equivalent to intersect TODO: implement intersect in Bitvector
    final IntList committeeSetBits = committeeBits.getAllSetBits();
    committeeSetBits.removeAll(other.getCommitteeBitsRequired().getAllSetBits());

    return committeeSetBits.isEmpty() && !aggregationBits.intersects(other.getAggregationBits());
  }

  @Override
  public SszBitlist getAggregationBits() {
    return aggregationBits;
  }

  @Override
  public SszBitvector getCommitteeBits() {
    return committeeBits;
  }

  @Override
  public Int2IntMap getCommitteesSize() {
    return committeesSize;
  }

  @Override
  public boolean requiresCommitteeBits() {
    return true;
  }
}

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
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;

@SuppressWarnings({"FieldCanBeFinal", "UnusedVariable"})
class AttestationBitsAggregatorElectra implements AttestationBitsAggregator {
  private SszBitlist aggregationBits;
  private SszBitvector committeeBits;
  private final Int2IntMap committeesSize;

  AttestationBitsAggregatorElectra(
      final SszBitlist aggregationBits,
      final SszBitvector committeeBits,
      Int2IntMap committeesSize) {
    this.aggregationBits = aggregationBits;
    this.committeeBits = committeeBits;
    this.committeesSize = committeesSize;
  }

  static AttestationBitsAggregator fromAttestationSchema(
      AttestationSchema<?> attestationSchema, Int2IntMap committeesSize) {
    return new AttestationBitsAggregatorElectra(
        attestationSchema.createEmptyAggregationBits(),
        attestationSchema.createEmptyCommitteeBits().orElseThrow(),
        committeesSize);
  }

  @Override
  public void aggregateWith(AttestationBitsAggregator other) {
    aggregationBits = aggregationBits.or(other.getAggregationBits());
  }

  @Override
  public void aggregateWith(Attestation other) {
    // Assumption: can aggregate are exactly the same or non-overlapping
    final SszBitvector otherCommitteeBits = other.getCommitteeBitsRequired();
    final SszBitvector aggregatedCommitteeBits = committeeBits.or(otherCommitteeBits);
    if (aggregatedCommitteeBits.getBitCount() == committeeBits.getBitCount()) {
      // they were the same, let's simply aggregate aggregationBits too
      aggregationBits = aggregationBits.or(other.getAggregationBits());
      committeeBits = aggregatedCommitteeBits;
      return;
    }

    final Int2IntMap committeeBitsStartingPositions =
        calculateCommitteeStartingPositions(committeeBits);
    final Int2IntMap otherCommitteeBitsStartingPositions =
        calculateCommitteeStartingPositions(otherCommitteeBits);
    final Int2IntMap aggregatedCommitteeBitsStartingPositions =
        calculateCommitteeStartingPositions(aggregatedCommitteeBits);

    final IntList aggregatedCommitteeIndices = aggregatedCommitteeBits.getAllSetBits();

    // create an aggregation bit big as last boundary for last committee bit
    final int lastCommitteeIndex =
        aggregatedCommitteeIndices.getInt(aggregatedCommitteeIndices.size() - 1);
    final int lastCommitteeStartingPosition =
        aggregatedCommitteeBitsStartingPositions.get(lastCommitteeIndex);

    final IntList aggregationIndices = new IntArrayList();

    // aggregateBits contains a new set of bits

    aggregatedCommitteeBits
        .streamAllSetBits()
        .forEach(
            committeeIndex -> {
              int committeeSize = committeesSize.get(committeeIndex);
              int destinationStart = aggregatedCommitteeBitsStartingPositions.get(committeeIndex);

              final SszBitlist source;
              final int sourceStartingPosition;
              if (committeeBitsStartingPositions.containsKey(committeeIndex)) {
                source = aggregationBits;
                sourceStartingPosition = committeeBitsStartingPositions.get(committeeIndex);
              } else {
                source = other.getAggregationBits();
                sourceStartingPosition = otherCommitteeBitsStartingPositions.get(committeeIndex);
              }

              IntStream.range(0, committeeSize)
                  .forEach(
                      positionInCommittee -> {
                        if (source.getBit(sourceStartingPosition + positionInCommittee)) {
                          aggregationIndices.add(destinationStart + positionInCommittee);
                        }
                      });
            });

    committeeBits = aggregatedCommitteeBits;
    aggregationBits =
        aggregationBits
            .getSchema()
            .ofBits(
                lastCommitteeStartingPosition + committeesSize.get(lastCommitteeIndex),
                aggregationIndices.toIntArray());
  }

  private Int2IntMap calculateCommitteeStartingPositions(final SszBitvector committeeBits) {
    final Int2IntMap committeeBitsStartingPositions = new Int2IntOpenHashMap();
    final IntList committeeIndices = committeeBits.getAllSetBits();
    int currentOffset = 0;
    for (final int index : committeeIndices) {
      committeeBitsStartingPositions.put(index, currentOffset);
      currentOffset += committeesSize.get(index);
    }

    return committeeBitsStartingPositions;
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
    // TODO support aggregation of partially overlapping committees?

    final IntList committeeSetBits = committeeBits.getAllSetBits();
    committeeSetBits.removeAll(other.getCommitteeBitsRequired().getAllSetBits());

    // we can aggregate only if the set are exactly the same (empty result) or they are completely
    // disjointed (size remains the same)
    boolean committeeBitsAggregatable =
        committeeSetBits.isEmpty() || committeeSetBits.size() == committeeBits.getBitCount();

    // same committees bits and non-overlapping

    boolean sameCommitteesWithDisjointedAggregationBits =
        committeeSetBits.isEmpty() && !aggregationBits.intersects(other.getAggregationBits());
    boolean disjointedCommittees = committeeSetBits.size() == committeeBits.getBitCount();

    return sameCommitteesWithDisjointedAggregationBits || disjointedCommittees;
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

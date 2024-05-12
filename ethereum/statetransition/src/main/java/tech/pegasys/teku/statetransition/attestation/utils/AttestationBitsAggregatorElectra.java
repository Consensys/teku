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

import com.google.common.base.MoreObjects;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;

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
  public void or(AttestationBitsAggregator other) {
    or(other.getCommitteeBits(), other.getAggregationBits(), false);
  }

  @Override
  public boolean aggregateWith(Attestation other) {
    return or(other.getCommitteeBitsRequired(), other.getAggregationBits(), true);
  }

  @Override
  public void or(Attestation other) {
    or(other.getCommitteeBitsRequired(), other.getAggregationBits(), false);
  }

  private static class CannotAggregateException extends RuntimeException {}

  private boolean or(
      final SszBitvector otherCommitteeBits,
      final SszBitlist otherAggregatedBits,
      final boolean isAggregation) {

    final SszBitvector aggregatedCommitteeBits = committeeBits.or(otherCommitteeBits);

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

    try {
      aggregatedCommitteeBits
          .streamAllSetBits()
          .forEach(
              committeeIndex -> {
                int committeeSize = committeesSize.get(committeeIndex);
                int destinationStart = aggregatedCommitteeBitsStartingPositions.get(committeeIndex);

                final List<SszBitlist> sources = new ArrayList<>();
                final List<Integer> sourcesStartingPosition = new ArrayList<>();
                if (committeeBitsStartingPositions.containsKey(committeeIndex)) {
                  sources.add(aggregationBits);
                  sourcesStartingPosition.add(committeeBitsStartingPositions.get(committeeIndex));
                }
                if (otherCommitteeBitsStartingPositions.containsKey(committeeIndex)) {
                  sources.add(otherAggregatedBits);
                  sourcesStartingPosition.add(
                      otherCommitteeBitsStartingPositions.get(committeeIndex));
                }

                IntStream.range(0, committeeSize)
                    .forEach(
                        positionInCommittee -> {
                          if (orSingleBit(
                              positionInCommittee,
                              sources,
                              sourcesStartingPosition,
                              isAggregation)) {
                            aggregationIndices.add(destinationStart + positionInCommittee);
                          }
                        });
              });
    } catch (final CannotAggregateException __) {
      return false;
    }

    committeeBits = aggregatedCommitteeBits;
    aggregationBits =
        aggregationBits
            .getSchema()
            .ofBits(
                lastCommitteeStartingPosition + committeesSize.get(lastCommitteeIndex),
                aggregationIndices.toIntArray());

    return true;
  }

  private boolean orSingleBit(
      final int positionInCommittee,
      final List<SszBitlist> sources,
      final List<Integer> sourcesStartingPosition,
      final boolean isAggregating) {
    boolean aggregatedBit = false;
    for (int s = 0; s < sources.size(); s++) {
      final boolean sourceBit =
          sources.get(s).getBit(sourcesStartingPosition.get(s) + positionInCommittee);

      if (!aggregatedBit && sourceBit) {
        aggregatedBit = true;
      } else if (isAggregating && aggregatedBit && sourceBit) {
        throw new CannotAggregateException();
      }
    }
    return aggregatedBit;
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
  public boolean isSuperSetOf(Attestation other) {
    if (!committeeBits.isSuperSetOf(other.getCommitteeBitsRequired())) {
      return false;
    }

    if (committeeBits.equals(other.getCommitteeBitsRequired())) {
      return aggregationBits.isSuperSetOf(other.getAggregationBits());
    }

    final SszBitvector otherCommitteeBits = other.getCommitteeBitsRequired();

    final Int2IntMap committeeBitsStartingPositions =
        calculateCommitteeStartingPositions(committeeBits);
    final Int2IntMap otherCommitteeBitsStartingPositions =
        calculateCommitteeStartingPositions(otherCommitteeBits);

    final SszBitvector commonCommittees = committeeBits.and(otherCommitteeBits);

    return commonCommittees
        .streamAllSetBits()
        .mapToObj(
            committeeIndex -> {
              int committeeSize = committeesSize.get(committeeIndex);

              final int startingPosition = committeeBitsStartingPositions.get(committeeIndex);
              final int otherStartingPosition =
                  otherCommitteeBitsStartingPositions.get(committeeIndex);

              return IntStream.range(0, committeeSize)
                  .anyMatch(
                      positionInCommittee ->
                          other
                                  .getAggregationBits()
                                  .getBit(otherStartingPosition + positionInCommittee)
                              && !aggregationBits.getBit(startingPosition + positionInCommittee));
            })
        .noneMatch(aBitFoundInOtherButNotInThis -> aBitFoundInOtherButNotInThis);
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

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("aggregationBits", aggregationBits)
        .add("committeeBits", committeeBits)
        .add("committeesSize", committeesSize)
        .toString();
  }
}

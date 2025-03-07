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
import java.util.BitSet;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;

class AttestationBitsAggregatorElectra implements AttestationBitsAggregator {
  private SszBitlist aggregationBits;
  private SszBitvector committeeBits;
  private Int2IntMap committeeBitsStartingPositions;
  private final Int2IntMap committeesSize;

  AttestationBitsAggregatorElectra(
      final SszBitlist aggregationBits,
      final SszBitvector committeeBits,
      final Int2IntMap committeesSize) {
    this.aggregationBits = aggregationBits;
    this.committeeBits = committeeBits;
    this.committeesSize = committeesSize;
    this.committeeBitsStartingPositions = calculateCommitteeStartingPositions(committeeBits);
  }

  static AttestationBitsAggregator fromAttestationSchema(
      final AttestationSchema<?> attestationSchema, final Int2IntMap committeesSize) {
    return new AttestationBitsAggregatorElectra(
        attestationSchema.createEmptyAggregationBits(),
        attestationSchema.createEmptyCommitteeBits().orElseThrow(),
        committeesSize);
  }

  @Override
  public void or(final AttestationBitsAggregator other) {
    or(other.getCommitteeBits(), other.getAggregationBits(), false);
  }

  @Override
  public boolean aggregateWith(final Attestation other) {
    return or(other.getCommitteeBitsRequired(), other.getAggregationBits(), true);
  }

  @Override
  public void or(final Attestation other) {
    or(other.getCommitteeBitsRequired(), other.getAggregationBits(), false);
  }

  private static class CannotAggregateException extends RuntimeException {}

  private boolean or(
      final SszBitvector otherCommitteeBits,
      final SszBitlist otherAggregatedBits,
      final boolean isAggregation) {
    if(isAggregation && otherAggregatedBits.getBitCount() == 1) {
      // optimization for single aggregation
      final int otherCommitteeIndex = otherCommitteeBits.getLastSetBitIndex();
      final int otherAggregatedBit = otherAggregatedBits.streamAllSetBits().findFirst().getAsInt();
      final int startingPosition = committeeBitsStartingPositions.getOrDefault(otherCommitteeIndex, -1);
      if(startingPosition != -1) {
        if(aggregationBits.getBit(startingPosition + otherAggregatedBit)) {
          throw new CannotAggregateException();
        }
        aggregationBits =
                aggregationBits.withBit(startingPosition + otherAggregatedBit);
        return true;
      }
    }

    final SszBitvector combinedCommitteeBits = committeeBits.or(otherCommitteeBits);

    final Int2IntMap otherCommitteeBitsStartingPositions =
        calculateCommitteeStartingPositions(otherCommitteeBits);
    final Int2IntMap aggregatedCommitteeBitsStartingPositions =
        calculateCommitteeStartingPositions(combinedCommitteeBits);

    // create an aggregation bit big as last boundary for last committee bit
    final int lastCommitteeIndex = combinedCommitteeBits.getLastSetBitIndex();
    final int lastCommitteeStartingPosition =
        aggregatedCommitteeBitsStartingPositions.get(lastCommitteeIndex);
    final int combinedAggregationBitsSize =
        lastCommitteeStartingPosition + committeesSize.get(lastCommitteeIndex);

    final BitSet combinedAggregationIndices = new BitSet(combinedAggregationBitsSize);

    // let's go over all aggregated committees to calculate indices for the combined aggregation
    // bits
    try {
      combinedCommitteeBits
          .streamAllSetBits()
          .forEach(
              committeeIndex -> {
                int committeeSize = committeesSize.get(committeeIndex);
                int destinationStart = aggregatedCommitteeBitsStartingPositions.get(committeeIndex);

                SszBitlist source1 = null, maybeSource2 = null;
                int source1StartingPosition, source2StartingPosition;

                source1StartingPosition = committeeBitsStartingPositions.getOrDefault(committeeIndex, -1);
                if (source1StartingPosition != -1) {
                  source1 = aggregationBits;
                }
                source2StartingPosition =
                        otherCommitteeBitsStartingPositions.getOrDefault(committeeIndex, -1);
                if (source2StartingPosition != -1) {
                  if (source1 != null) {
                    maybeSource2 = otherAggregatedBits;
                  } else {
                    source1 = otherAggregatedBits;
                    source1StartingPosition = source2StartingPosition;
                  }
                }

                // Now that we know:
                // 1. which aggregationBits (this or other or both) will contribute to the result
                // 2. the offset of the committee for each contributing aggregation bits
                // We can go over the committee and calculate the combined aggregate bits
                for (int positionInCommittee = 0;
                    positionInCommittee < committeeSize;
                    positionInCommittee++) {
                  if (orSingleBit(
                      positionInCommittee,
                      source1,
                      source1StartingPosition,
                      maybeSource2,
                      source2StartingPosition,
                      isAggregation)) {
                    combinedAggregationIndices.set(destinationStart + positionInCommittee);
                  }
                }
              });
    } catch (final CannotAggregateException __) {
      return false;
    }

    committeeBits = combinedCommitteeBits;
    aggregationBits =
        aggregationBits
            .getSchema()
            .wrapBitSet(combinedAggregationBitsSize, combinedAggregationIndices);
    committeeBitsStartingPositions = aggregatedCommitteeBitsStartingPositions;

    return true;
  }

  private boolean orSingleBit(
      final int positionInCommittee,
      final SszBitlist source1,
      final int source1StartingPosition,
      final SszBitlist maybeSource2,
      final int source2StartingPosition,
      final boolean isAggregation) {

    final boolean source1Bit = source1.getBit(source1StartingPosition + positionInCommittee);

    if (maybeSource2 == null) {
      return source1Bit;
    }

    final boolean source2Bit = maybeSource2.getBit(source2StartingPosition + positionInCommittee);

    if (isAggregation && source1Bit && source2Bit) {
      throw new CannotAggregateException();
    }

    return source1Bit || source2Bit;
  }

  private Int2IntMap calculateCommitteeStartingPositions(final SszBitvector committeeBits) {
    final Int2IntMap committeeBitsStartingPositions = new Int2IntOpenHashMap();
    final int[] currentOffset = {0};
    committeeBits
        .streamAllSetBits()
        .forEach(
            index -> {
              committeeBitsStartingPositions.put(index, currentOffset[0]);
              currentOffset[0] += committeesSize.get(index);
            });

    return committeeBitsStartingPositions;
  }

  @Override
  public boolean isSuperSetOf(final Attestation other) {
    if (!committeeBits.isSuperSetOf(other.getCommitteeBitsRequired())) {
      return false;
    }

    if (committeeBits.getBitCount() == other.getCommitteeBitsRequired().getBitCount()) {
      // this committeeBits is a superset of the other, and bit count is the same, so they are the
      // same set and we can directly compare aggregation bits.
      return aggregationBits.isSuperSetOf(other.getAggregationBits());
    }
    if(other.getAggregationBits().getBitCount() == 1) {
      // optimization for single aggregation
      final int otherCommitteeIndex = other.getCommitteeBitsRequired().getLastSetBitIndex();
      final int otherAggregatedBit = other.getAggregationBits().streamAllSetBits().findFirst().getAsInt();
      final int startingPosition = committeeBitsStartingPositions.getOrDefault(otherCommitteeIndex, -1);
      if(startingPosition != -1) {
        return aggregationBits.getBit(startingPosition + otherAggregatedBit);
      }
    }

    final SszBitvector otherCommitteeBits = other.getCommitteeBitsRequired();

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
        .add("committeeBitsStartingPositions", committeeBitsStartingPositions)
        .toString();
  }
}

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
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;

class AttestationBitsAggregatorElectra implements AttestationBitsAggregator {

  private final SszBitlistSchema<?> aggregationBitsSchema;
  private final SszBitvectorSchema<?> committeeBitsSchema;
  private final Int2IntMap committeesSize;

  private Int2ObjectMap<BitSet> committeeAggregationBitsMap;
  private BitSet committeeBits;

  private SszBitlist cachedAggregationBits = null;
  private SszBitvector cachedCommitteeBits = null;

  AttestationBitsAggregatorElectra(
      final SszBitlist initialAggregationBits,
      final SszBitvector initialCommitteeBits,
      final Int2IntMap committeesSize) {
    this.aggregationBitsSchema = initialAggregationBits.getSchema();
    this.committeeBitsSchema = initialCommitteeBits.getSchema();
    this.committeesSize = Objects.requireNonNull(committeesSize, "committeesSize cannot be null");
    this.committeeBits = initialCommitteeBits.getAsBitSet();
    this.committeeAggregationBitsMap =
        parseAggregationBits(initialAggregationBits, this.committeeBits, this.committeesSize);
  }

  static AttestationBitsAggregator fromAttestationSchema(
      final AttestationSchema<?> attestationSchema, final Int2IntMap committeesSize) {
    final SszBitlist emptyAggregationBits = attestationSchema.createEmptyAggregationBits();
    final SszBitvector emptyCommitteeBits =
        attestationSchema
            .createEmptyCommitteeBits()
            .orElseThrow(
                () -> new IllegalStateException("Electra schema must provide committee bits"));
    return new AttestationBitsAggregatorElectra(
        emptyAggregationBits, emptyCommitteeBits, committeesSize);
  }

  private static Int2ObjectMap<BitSet> parseAggregationBits(
      final SszBitlist aggregationBits,
      final BitSet committeeIndices,
      final Int2IntMap committeesSizeMap) {
    final Int2ObjectMap<BitSet> result = new Int2ObjectOpenHashMap<>();

    int currentOffset = 0;
    for (int committeeIndex = committeeIndices.nextSetBit(0);
        committeeIndex >= 0;
        committeeIndex = committeeIndices.nextSetBit(committeeIndex + 1)) {

      final int committeeSize = committeesSizeMap.getOrDefault(committeeIndex, 0);
      if (committeeSize > 0) {
        int sliceEnd =
            Math.min(currentOffset + committeeSize, aggregationBits.getLastSetBitIndex() + 1);
        final BitSet committeeBits = aggregationBits.getAsBitSet(currentOffset, sliceEnd);
        result.put(committeeIndex, committeeBits);
      }
      currentOffset += committeeSize; // Always advance by the declared committee size
    }
    return result;
  }

  @Override
  public void or(final AttestationBitsAggregator other) {
    if (!(other instanceof AttestationBitsAggregatorElectra otherElectra)) {
      throw new IllegalArgumentException(
          "AttestationBitsAggregatorElectra.or requires an argument of the same type.");
    }

    performMerge(otherElectra.committeeBits, otherElectra.committeeAggregationBitsMap, false);
  }

  @Override
  public boolean aggregateWith(final Attestation other) {
    final BitSet otherCommitteeBits = other.getCommitteeBitsRequired().getAsBitSet();
    final Int2ObjectMap<BitSet> otherParsedAggregationMap =
        parseAggregationBits(other.getAggregationBits(), otherCommitteeBits, this.committeesSize);
    return performMerge(otherCommitteeBits, otherParsedAggregationMap, true);
  }

  @Override
  public void or(final Attestation other) {
    final BitSet otherCommitteeBits = other.getCommitteeBitsRequired().getAsBitSet();
    final Int2ObjectMap<BitSet> otherParsedAggregationMap =
        parseAggregationBits(other.getAggregationBits(), otherCommitteeBits, this.committeesSize);
    performMerge(otherCommitteeBits, otherParsedAggregationMap, false);
  }

  private boolean performMerge(
      final BitSet otherCommitteeBits,
      final Int2ObjectMap<BitSet> otherCommitteeAggregationBitsMap,
      final boolean isAggregation) {
    final BitSet mergedCommitteeBits = (BitSet) this.committeeBits.clone();
    mergedCommitteeBits.or(otherCommitteeBits);

    final Int2ObjectMap<BitSet> targetAggregationBitsMap;

    if (isAggregation) {
      // If aggregating, we need to work on copies
      targetAggregationBitsMap = new Int2ObjectOpenHashMap<>();
      for (final Int2ObjectMap.Entry<BitSet> entry :
          this.committeeAggregationBitsMap.int2ObjectEntrySet()) {
        targetAggregationBitsMap.put(entry.getIntKey(), (BitSet) entry.getValue().clone());
      }
    } else {
      // if not aggregating, we can modify in place
      targetAggregationBitsMap = this.committeeAggregationBitsMap;
    }

    for (int committeeIndex = mergedCommitteeBits.nextSetBit(0);
        committeeIndex >= 0;
        committeeIndex = mergedCommitteeBits.nextSetBit(committeeIndex + 1)) {

      final boolean inThis = this.committeeBits.get(committeeIndex);
      final boolean inOther = otherCommitteeBits.get(committeeIndex);

      if (inThis && inOther) {
        final BitSet otherAggregationBitsForCommittee =
            otherCommitteeAggregationBitsMap.get(committeeIndex);
        final BitSet targetAggregationBitsForCommittee =
            targetAggregationBitsMap.get(committeeIndex);

        if (isAggregation) {
          // For intersection check, use the original bits of 'this'
          final BitSet thisAggregationBitsForCommittee =
              this.committeeAggregationBitsMap.get(committeeIndex);
          if (thisAggregationBitsForCommittee != null
              && thisAggregationBitsForCommittee.intersects(otherAggregationBitsForCommittee)) {
            return false;
          }
        }

        targetAggregationBitsForCommittee.or(otherAggregationBitsForCommittee);

      } else if (inOther) {
        // Committee only in 'other'.
        final BitSet otherDataForCommittee = otherCommitteeAggregationBitsMap.get(committeeIndex);

        targetAggregationBitsMap.put(committeeIndex, (BitSet) otherDataForCommittee.clone());
      }
      // Committee only in 'this', do nothing.
    }

    this.committeeBits = mergedCommitteeBits;
    if (isAggregation) {
      this.committeeAggregationBitsMap = targetAggregationBitsMap;
    }

    invalidateCache();
    return true;
  }

  @Override
  public boolean isSuperSetOf(final Attestation other) {
    final BitSet otherInternalCommitteeBits = other.getCommitteeBitsRequired().getAsBitSet();

    final BitSet committeeIntersection = (BitSet) this.committeeBits.clone();
    committeeIntersection.and(otherInternalCommitteeBits);
    if (!committeeIntersection.equals(otherInternalCommitteeBits)) {
      return false;
    }

    final Int2ObjectMap<BitSet> otherCommitteeAggregationBitsMap =
        parseAggregationBits(
            other.getAggregationBits(), otherInternalCommitteeBits, this.committeesSize);

    for (int committeeIndex = otherInternalCommitteeBits.nextSetBit(0);
        committeeIndex >= 0;
        committeeIndex = otherInternalCommitteeBits.nextSetBit(committeeIndex + 1)) {

      final BitSet thisAggregationBitsForCommittee =
          this.committeeAggregationBitsMap.get(committeeIndex);
      final BitSet otherAggregationBitsForCommittee =
          otherCommitteeAggregationBitsMap.get(committeeIndex);

      if (thisAggregationBitsForCommittee == null) {
        return false;
      }
      if (otherAggregationBitsForCommittee == null || otherAggregationBitsForCommittee.isEmpty()) {
        continue;
      }

      final BitSet otherBitsNotInThis = (BitSet) otherAggregationBitsForCommittee.clone();
      otherBitsNotInThis.andNot(thisAggregationBitsForCommittee);
      if (!otherBitsNotInThis.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public SszBitlist getAggregationBits() {
    if (cachedAggregationBits != null) {
      return cachedAggregationBits;
    }
    final List<Integer> committeeIndicesInOrder = new ArrayList<>();
    for (int i = committeeBits.nextSetBit(0); i >= 0; i = committeeBits.nextSetBit(i + 1)) {
      committeeIndicesInOrder.add(i);
    }
    // No explicit sort needed as BitSet.nextSetBit() iterates in ascending order.

    int totalBitlistSize = 0;
    for (final int committeeIndex : committeeIndicesInOrder) {
      totalBitlistSize += this.committeesSize.getOrDefault(committeeIndex, 0);
    }

    final BitSet combinedAggregationBits = new BitSet(totalBitlistSize);
    int currentOffset = 0;
    for (final int committeeIndex : committeeIndicesInOrder) {
      final BitSet committeeBitsData = this.committeeAggregationBitsMap.get(committeeIndex);
      final int committeeSize = this.committeesSize.getOrDefault(committeeIndex, 0);

      if (committeeBitsData != null && committeeSize > 0) {
        for (int bitIndex = committeeBitsData.nextSetBit(0);
            bitIndex >= 0 && bitIndex < committeeSize;
            bitIndex = committeeBitsData.nextSetBit(bitIndex + 1)) {
          combinedAggregationBits.set(currentOffset + bitIndex);
        }
      }
      currentOffset += committeeSize;
    }
    cachedAggregationBits =
        aggregationBitsSchema.wrapBitSet(totalBitlistSize, combinedAggregationBits);
    return cachedAggregationBits;
  }

  @Override
  public SszBitvector getCommitteeBits() {
    if (cachedCommitteeBits == null) {
      cachedCommitteeBits =
          committeeBitsSchema.wrapBitSet(committeeBitsSchema.getLength(), this.committeeBits);
    }
    return cachedCommitteeBits;
  }

  private void invalidateCache() {
    this.cachedAggregationBits = null;
    this.cachedCommitteeBits = null;
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
    long totalSetBits = 0;
    if (committeeAggregationBitsMap != null) {
      for (final BitSet bitSet : committeeAggregationBitsMap.values()) {
        if (bitSet != null) {
          totalSetBits += bitSet.cardinality();
        }
      }
    }
    return MoreObjects.toStringHelper(this)
        .add("committeeBits", committeeBits.cardinality())
        .add("committeeAggregationBitsMap", totalSetBits)
        .add("committeesSize", committeesSize.size())
        .add("cached", cachedAggregationBits != null || cachedCommitteeBits != null)
        .toString();
  }
}

/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.common.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uintToBytes;

import com.google.common.primitives.UnsignedBytes;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.crypto.Sha256;
import tech.pegasys.teku.infrastructure.ssz.Merkleizable;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.ForkData;
import tech.pegasys.teku.spec.datastructures.state.SigningData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;

public class MiscHelpers {

  // Math.pow(2, 8) - 1;
  public static final UInt64 MAX_RANDOM_BYTE = UInt64.valueOf(255);

  protected final SpecConfig specConfig;

  private static final byte[] EMPTY_HASH = Bytes.EMPTY.toArrayUnsafe();

  public MiscHelpers(final SpecConfig specConfig) {
    this.specConfig = specConfig;
  }

  public int computeShuffledIndex(int index, int indexCount, Bytes32 seed) {
    checkArgument(index < indexCount, "CommitteeUtil.computeShuffledIndex1");
    final Sha256 sha256 = new Sha256();

    int indexRet = index;
    final int shuffleRoundCount = specConfig.getShuffleRoundCount();

    for (int round = 0; round < shuffleRoundCount; round++) {

      Bytes roundAsByte = Bytes.of((byte) round);

      // This needs to be unsigned modulo.
      int pivot =
          bytesToUInt64(sha256.wrappedDigest(seed, roundAsByte).slice(0, 8))
              .mod(indexCount)
              .intValue();
      int flip = Math.floorMod(pivot + indexCount - indexRet, indexCount);
      int position = Math.max(indexRet, flip);

      Bytes positionDiv256 = uintToBytes(Math.floorDiv(position, 256L), 4);
      byte[] hashBytes = sha256.digest(seed, roundAsByte, positionDiv256);

      int bitIndex = position & 0xff;
      int theByte = hashBytes[bitIndex / 8];
      int theBit = (theByte >> (bitIndex & 0x07)) & 1;
      if (theBit != 0) {
        indexRet = flip;
      }
    }

    return indexRet;
  }

  public int computeProposerIndex(BeaconState state, IntList indices, Bytes32 seed) {
    checkArgument(!indices.isEmpty(), "compute_proposer_index indices must not be empty");
    final Sha256 sha256 = new Sha256();
    int i = 0;
    final int total = indices.size();
    byte[] hash = null;
    while (true) {
      int candidateIndex = indices.getInt(computeShuffledIndex(i % total, total, seed));
      if (i % 32 == 0) {
        hash = sha256.digest(seed, uint64ToBytes(Math.floorDiv(i, 32L)));
      }
      int randomByte = UnsignedBytes.toInt(hash[i % 32]);
      UInt64 effectiveBalance = state.getValidators().get(candidateIndex).getEffectiveBalance();
      if (effectiveBalance
          .times(MAX_RANDOM_BYTE)
          .isGreaterThanOrEqualTo(specConfig.getMaxEffectiveBalance().times(randomByte))) {
        return candidateIndex;
      }
      i++;
    }
  }

  public UInt64 computeEpochAtSlot(UInt64 slot) {
    return slot.dividedBy(specConfig.getSlotsPerEpoch());
  }

  public UInt64 computeStartSlotAtEpoch(UInt64 epoch) {
    return epoch.times(specConfig.getSlotsPerEpoch());
  }

  public UInt64 computeTimeAtSlot(BeaconState state, UInt64 slot) {
    UInt64 slotsSinceGenesis = slot.minus(SpecConfig.GENESIS_SLOT);
    return state.getGenesisTime().plus(slotsSinceGenesis.times(specConfig.getSecondsPerSlot()));
  }

  public boolean isSlotAtNthEpochBoundary(
      final UInt64 blockSlot, final UInt64 parentSlot, final int n) {
    checkArgument(n > 0, "Parameter n must be greater than 0");
    final UInt64 blockEpoch = computeEpochAtSlot(blockSlot);
    final UInt64 parentEpoch = computeEpochAtSlot(parentSlot);
    return blockEpoch.dividedBy(n).isGreaterThan(parentEpoch.dividedBy(n));
  }

  public UInt64 computeActivationExitEpoch(UInt64 epoch) {
    return epoch.plus(UInt64.ONE).plus(specConfig.getMaxSeedLookahead());
  }

  public UInt64 getEarliestQueryableSlotForBeaconCommitteeAtTargetSlot(final UInt64 slot) {
    final UInt64 epoch = computeEpochAtSlot(slot);
    return getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(epoch);
  }

  public UInt64 getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(final UInt64 epoch) {
    final UInt64 previousEpoch = epoch.compareTo(UInt64.ZERO) > 0 ? epoch.minus(UInt64.ONE) : epoch;
    return computeStartSlotAtEpoch(previousEpoch);
  }

  public IntList computeCommittee(
      BeaconState state, IntList indices, Bytes32 seed, int index, int count) {
    int start = Math.floorDiv(indices.size() * index, count);
    int end = Math.floorDiv(indices.size() * (index + 1), count);
    return computeCommitteeShuffle(state, indices, seed, start, end);
  }

  private IntList computeCommitteeShuffle(
      BeaconState state, IntList indices, Bytes32 seed, int fromIndex, int toIndex) {
    if (fromIndex < toIndex) {
      int indexCount = indices.size();
      checkArgument(fromIndex < indexCount, "CommitteeUtil.getShuffledIndex1");
      checkArgument(toIndex <= indexCount, "CommitteeUtil.getShuffledIndex1");
    }
    return BeaconStateCache.getTransitionCaches(state)
        .getCommitteeShuffle()
        .get(seed, s -> shuffleList(indices, s))
        .subList(fromIndex, toIndex);
  }

  IntList shuffleList(IntList input, Bytes32 seed) {
    final int[] indices = input.toIntArray();
    shuffleList(indices, seed);
    return IntList.of(indices);
  }

  public void shuffleList(int[] input, Bytes32 seed) {
    final Sha256 sha256 = new Sha256();

    int listSize = input.length;
    if (listSize == 0) {
      return;
    }

    for (int round = specConfig.getShuffleRoundCount() - 1; round >= 0; round--) {

      final Bytes roundAsByte = Bytes.of((byte) round);

      // This needs to be unsigned modulo.
      final Bytes hash = sha256.wrappedDigest(seed, roundAsByte);
      int pivot = bytesToUInt64(hash.slice(0, 8)).mod(listSize).intValue();

      byte[] hashBytes = EMPTY_HASH;
      int mirror1 = (pivot + 2) / 2;
      int mirror2 = (pivot + listSize) / 2;
      for (int i = mirror1; i <= mirror2; i++) {

        int flip, bitIndex;
        if (i <= pivot) {
          flip = pivot - i;
          bitIndex = i & 0xff;
          if (bitIndex == 0 || i == mirror1) {
            hashBytes = sha256.digest(seed, roundAsByte, uintToBytes(i / 256, 4));
          }
        } else {
          flip = pivot + listSize - i;
          bitIndex = flip & 0xff;
          if (bitIndex == 0xff || i == pivot + 1) {
            hashBytes = sha256.digest(seed, roundAsByte, uintToBytes(flip / 256, 4));
          }
        }

        int theByte = hashBytes[bitIndex / 8];
        int theBit = (theByte >> (bitIndex & 0x07)) & 1;
        if (theBit != 0) {
          int tmp = input[i];
          input[i] = input[flip];
          input[flip] = tmp;
        }
      }
    }
  }

  public Bytes computeSigningRoot(Merkleizable object, Bytes32 domain) {
    return new SigningData(object.hashTreeRoot(), domain).hashTreeRoot();
  }

  public Bytes computeSigningRoot(UInt64 number, Bytes32 domain) {
    SigningData domainWrappedObject = new SigningData(SszUInt64.of(number).hashTreeRoot(), domain);
    return domainWrappedObject.hashTreeRoot();
  }

  public Bytes32 computeSigningRoot(Bytes bytes, Bytes32 domain) {
    SigningData domainWrappedObject =
        new SigningData(SszByteVector.computeHashTreeRoot(bytes), domain);
    return domainWrappedObject.hashTreeRoot();
  }

  public Bytes4 computeForkDigest(Bytes4 currentVersion, Bytes32 genesisValidatorsRoot) {
    return new Bytes4(computeForkDataRoot(currentVersion, genesisValidatorsRoot).slice(0, 4));
  }

  public Bytes32 computeDomain(Bytes4 domainType) {
    return computeDomain(domainType, specConfig.getGenesisForkVersion(), Bytes32.ZERO);
  }

  public Bytes32 computeDomain(
      Bytes4 domainType, Bytes4 forkVersion, Bytes32 genesisValidatorsRoot) {
    final Bytes32 forkDataRoot = computeForkDataRoot(forkVersion, genesisValidatorsRoot);
    return computeDomain(domainType, forkDataRoot);
  }

  private Bytes32 computeDomain(final Bytes4 domainType, final Bytes32 forkDataRoot) {
    return Bytes32.wrap(Bytes.concatenate(domainType.getWrappedBytes(), forkDataRoot.slice(0, 28)));
  }

  private Bytes32 computeForkDataRoot(Bytes4 currentVersion, Bytes32 genesisValidatorsRoot) {
    return new ForkData(currentVersion, genesisValidatorsRoot).hashTreeRoot();
  }

  public boolean isMergeTransitionComplete(final BeaconState state) {
    return false;
  }
}

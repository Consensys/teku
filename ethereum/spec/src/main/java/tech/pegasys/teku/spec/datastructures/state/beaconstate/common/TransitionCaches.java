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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.TekuPair;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.CaffeineCache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.collections.cache.NoOpCache;
import tech.pegasys.teku.infrastructure.collections.cache.StripedCache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ProgressiveTotalBalancesUpdates;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;

/** The container class for all transition caches. */
public class TransitionCaches {

  private static final int MAX_ACTIVE_VALIDATORS_CACHE = 8;
  private static final int MAX_BEACON_PROPOSER_INDEX_CACHE = 1;
  private static final int MAX_BEACON_COMMITTEE_CACHE = 64 * 64;
  private static final int MAX_BEACON_COMMITTEES_SIZE_CACHE = 64;
  private static final int MAX_TOTAL_ACTIVE_BALANCE_CACHE = 2;
  private static final int MAX_COMMITTEE_SHUFFLE_CACHE = 3;
  private static final int MAX_EFFECTIVE_BALANCE_CACHE = 1;
  private static final int MAX_SYNC_COMMITTEE_CACHE = 2;
  public static final int MAX_BASE_REWARD_PER_INCREMENT_CACHE = 1;

  private static final TransitionCaches NO_OP_INSTANCE =
      new TransitionCaches(
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          ValidatorIndexCache.NO_OP_INSTANCE,
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          ProgressiveTotalBalancesUpdates.NOOP) {

        @Override
        public TransitionCaches copy() {
          return this;
        }
      };

  /**
   * Creates new instance with clean caches. Uses: - CaffeineCache for beaconCommittee (high
   * contention, hot path) - LRUCache for other bounded caches (lightweight, copied frequently) -
   * StripedCache for unbounded validator caches (16x less lock contention)
   */
  public static TransitionCaches createNewEmpty() {
    return new TransitionCaches(
        LRUCache.create(MAX_ACTIVE_VALIDATORS_CACHE),
        LRUCache.create(MAX_BEACON_PROPOSER_INDEX_CACHE),
        CaffeineCache.create(MAX_BEACON_COMMITTEE_CACHE),
        LRUCache.create(MAX_BEACON_COMMITTEES_SIZE_CACHE),
        LRUCache.create(MAX_BEACON_COMMITTEE_CACHE),
        LRUCache.create(MAX_TOTAL_ACTIVE_BALANCE_CACHE),
        StripedCache.createUnbounded(),
        new ValidatorIndexCache(StripedCache.createUnbounded()),
        LRUCache.create(MAX_COMMITTEE_SHUFFLE_CACHE),
        LRUCache.create(MAX_EFFECTIVE_BALANCE_CACHE),
        LRUCache.create(MAX_SYNC_COMMITTEE_CACHE),
        LRUCache.create(MAX_BASE_REWARD_PER_INCREMENT_CACHE),
        ProgressiveTotalBalancesUpdates.NOOP);
  }

  /** Returns the instance which doesn't cache anything */
  public static TransitionCaches getNoOp() {
    return NO_OP_INSTANCE;
  }

  private final Cache<UInt64, IntList> activeValidators;
  private final Cache<UInt64, Integer> beaconProposerIndex;
  private final Cache<TekuPair<UInt64, UInt64>, IntList> beaconCommittee;
  private final Cache<UInt64, Int2IntMap> beaconCommitteesSize;
  private final Cache<UInt64, UInt64> attestersTotalBalance;
  private final Cache<UInt64, UInt64> totalActiveBalance;
  private final Cache<UInt64, BLSPublicKey> validatorsPubKeys;
  private final ValidatorIndexCache validatorIndexCache;
  private final Cache<Bytes32, IntList> committeeShuffle;
  private final Cache<UInt64, List<UInt64>> effectiveBalances;
  private final Cache<UInt64, UInt64> baseRewardPerIncrement;

  private final Cache<UInt64, Map<UInt64, SyncSubcommitteeAssignments>> syncCommitteeCache;

  private volatile Optional<TotalBalances> latestTotalBalances = Optional.empty();
  private volatile ProgressiveTotalBalancesUpdates progressiveTotalBalances;

  @FunctionalInterface
  public interface CacheFactory {
    <K, V> Cache<K, V> create(int capacity);
  }

  @FunctionalInterface
  public interface UnboundedCacheFactory {
    <K, V> Cache<K, V> create();
  }

  /**
   * Constructor for benchmarking - allows testing different cache implementations for both bounded
   * and unbounded caches.
   */
  @VisibleForTesting
  public TransitionCaches(
      final CacheFactory boundedCacheFactory, final UnboundedCacheFactory unboundedCacheFactory) {
    activeValidators = boundedCacheFactory.create(MAX_ACTIVE_VALIDATORS_CACHE);
    beaconProposerIndex = boundedCacheFactory.create(MAX_BEACON_PROPOSER_INDEX_CACHE);
    beaconCommittee = boundedCacheFactory.create(MAX_BEACON_COMMITTEE_CACHE);
    beaconCommitteesSize = boundedCacheFactory.create(MAX_BEACON_COMMITTEES_SIZE_CACHE);
    attestersTotalBalance = boundedCacheFactory.create(MAX_BEACON_COMMITTEE_CACHE);
    totalActiveBalance = boundedCacheFactory.create(MAX_TOTAL_ACTIVE_BALANCE_CACHE);
    validatorsPubKeys = unboundedCacheFactory.create();
    validatorIndexCache = new ValidatorIndexCache(unboundedCacheFactory.create());
    committeeShuffle = boundedCacheFactory.create(MAX_COMMITTEE_SHUFFLE_CACHE);
    effectiveBalances = boundedCacheFactory.create(MAX_EFFECTIVE_BALANCE_CACHE);
    syncCommitteeCache = boundedCacheFactory.create(MAX_SYNC_COMMITTEE_CACHE);
    baseRewardPerIncrement = boundedCacheFactory.create(MAX_BASE_REWARD_PER_INCREMENT_CACHE);
    progressiveTotalBalances = ProgressiveTotalBalancesUpdates.NOOP;
  }

  private TransitionCaches(
      final Cache<UInt64, IntList> activeValidators,
      final Cache<UInt64, Integer> beaconProposerIndex,
      final Cache<TekuPair<UInt64, UInt64>, IntList> beaconCommittee,
      final Cache<UInt64, Int2IntMap> beaconCommitteesSize,
      final Cache<UInt64, UInt64> attestersTotalBalance,
      final Cache<UInt64, UInt64> totalActiveBalance,
      final Cache<UInt64, BLSPublicKey> validatorsPubKeys,
      final ValidatorIndexCache validatorIndexCache,
      final Cache<Bytes32, IntList> committeeShuffle,
      final Cache<UInt64, List<UInt64>> effectiveBalances,
      final Cache<UInt64, Map<UInt64, SyncSubcommitteeAssignments>> syncCommitteeCache,
      final Cache<UInt64, UInt64> baseRewardPerIncrement,
      final ProgressiveTotalBalancesUpdates progressiveTotalBalances) {
    this.activeValidators = activeValidators;
    this.beaconProposerIndex = beaconProposerIndex;
    this.beaconCommittee = beaconCommittee;
    this.beaconCommitteesSize = beaconCommitteesSize;
    this.attestersTotalBalance = attestersTotalBalance;
    this.totalActiveBalance = totalActiveBalance;
    this.validatorsPubKeys = validatorsPubKeys;
    this.validatorIndexCache = validatorIndexCache;
    this.committeeShuffle = committeeShuffle;
    this.effectiveBalances = effectiveBalances;
    this.syncCommitteeCache = syncCommitteeCache;
    this.baseRewardPerIncrement = baseRewardPerIncrement;
    this.progressiveTotalBalances = progressiveTotalBalances;
  }

  public void setLatestTotalBalances(final TotalBalances totalBalances) {
    this.latestTotalBalances = Optional.of(totalBalances);
  }

  public Optional<TotalBalances> getLatestTotalBalances() {
    return latestTotalBalances;
  }

  public ProgressiveTotalBalancesUpdates getProgressiveTotalBalances() {
    return progressiveTotalBalances;
  }

  public void setProgressiveTotalBalances(
      final ProgressiveTotalBalancesUpdates progressiveTotalBalances) {
    this.progressiveTotalBalances = progressiveTotalBalances;
  }

  /** (epoch) -> (active validators) cache */
  public Cache<UInt64, IntList> getActiveValidators() {
    return activeValidators;
  }

  /** (slot) -> (beacon proposer index) cache */
  public Cache<UInt64, Integer> getBeaconProposerIndex() {
    return beaconProposerIndex;
  }

  /** (slot, committeeIndex) -> (committee) cache */
  public Cache<TekuPair<UInt64, UInt64>, IntList> getBeaconCommittee() {
    return beaconCommittee;
  }

  /** (slot) -> Map(committeeIndex, size of a committee) */
  public Cache<UInt64, Int2IntMap> getBeaconCommitteesSize() {
    return beaconCommitteesSize;
  }

  /** (slot) -> (total effective balance of attesters in slot) */
  public Cache<UInt64, UInt64> getAttestersTotalBalance() {
    return attestersTotalBalance;
  }

  /** (epoch) -> (total active balance) cache */
  public Cache<UInt64, UInt64> getTotalActiveBalance() {
    return totalActiveBalance;
  }

  /** (validator index) -> (validator pub key) cache */
  public Cache<UInt64, BLSPublicKey> getValidatorsPubKeys() {
    return validatorsPubKeys;
  }

  /**
   * (validator pub key) -> (validator index) cache
   *
   * <p>WARNING: Only contains mappings for public keys of validators whose indices are part of a
   * finalized state. Otherwise, the mapping will be retrieved from the state. Look at
   * https://eips.ethereum.org/EIPS/eip-6110#validator-index-invariant for more information. Check
   * index {@literal < } total validator count before looking up the cache.
   */
  public ValidatorIndexCache getValidatorIndexCache() {
    return validatorIndexCache;
  }

  /** (epoch committee seed) -> (validators shuffle for epoch) cache */
  public Cache<Bytes32, IntList> getCommitteeShuffle() {
    return committeeShuffle;
  }

  /**
   * (epoch) -> (validator effective balances) cache. Note that inactive validators report an
   * effective balance of 0.
   *
   * @return the effective balance cache
   */
  public Cache<UInt64, List<UInt64>> getEffectiveBalances() {
    return effectiveBalances;
  }

  /** (sync committee period) -> Map(validatorIndex?, Set(SubcommitteeIndex)) */
  public Cache<UInt64, Map<UInt64, SyncSubcommitteeAssignments>> getSyncCommitteeCache() {
    return syncCommitteeCache;
  }

  public Cache<UInt64, UInt64> getBaseRewardPerIncrement() {
    return baseRewardPerIncrement;
  }

  /**
   * Makes an independent copy which contains all the data in this instance. Modifications to the
   * returned caches shouldn't affect caches from this instance
   */
  public TransitionCaches copy() {
    return new TransitionCaches(
        activeValidators.copy(),
        beaconProposerIndex.copy(),
        beaconCommittee.copy(),
        beaconCommitteesSize.copy(),
        attestersTotalBalance.copy(),
        totalActiveBalance.copy(),
        validatorsPubKeys,
        validatorIndexCache,
        committeeShuffle.copy(),
        effectiveBalances.copy(),
        syncCommitteeCache.copy(),
        baseRewardPerIncrement.copy(),
        progressiveTotalBalances.copy());
  }
}

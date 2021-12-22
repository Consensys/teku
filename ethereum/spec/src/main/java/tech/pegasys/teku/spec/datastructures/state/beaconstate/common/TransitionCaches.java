/*
 * Copyright 2020 ConsenSys AG.
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

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.TekuPair;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.collections.cache.NoOpCache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;

/** The container class for all transition caches. */
public class TransitionCaches {

  private static final int MAX_ACTIVE_VALIDATORS_CACHE = 8;
  private static final int MAX_BEACON_PROPOSER_INDEX_CACHE = 1;
  private static final int MAX_BEACON_COMMITTEE_CACHE = 64 * 64;
  private static final int MAX_TOTAL_ACTIVE_BALANCE_CACHE = 2;
  private static final int MAX_COMMITTEE_SHUFFLE_CACHE = 2;
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
          ValidatorIndexCache.NO_OP_INSTANCE,
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache()) {

        @Override
        public TransitionCaches copy() {
          return this;
        }
      };

  /** Creates new instance with clean caches */
  public static TransitionCaches createNewEmpty() {
    return new TransitionCaches();
  }

  /** Returns the instance which doesn't cache anything */
  public static TransitionCaches getNoOp() {
    return NO_OP_INSTANCE;
  }

  private final Cache<UInt64, IntList> activeValidators;
  private final Cache<UInt64, Integer> beaconProposerIndex;
  private final Cache<TekuPair<UInt64, UInt64>, IntList> beaconCommittee;
  private final Cache<UInt64, UInt64> attestersTotalBalance;
  private final Cache<UInt64, UInt64> totalActiveBalance;
  private final Cache<UInt64, BLSPublicKey> validatorsPubKeys;
  private final ValidatorIndexCache validatorIndexCache;
  private final Cache<Bytes32, IntList> committeeShuffle;
  private final Cache<UInt64, List<UInt64>> effectiveBalances;
  private final Cache<UInt64, UInt64> baseRewardPerIncrement;

  private final Cache<UInt64, Map<UInt64, SyncSubcommitteeAssignments>> syncCommitteeCache;

  private volatile Optional<TotalBalances> latestTotalBalances = Optional.empty();

  private TransitionCaches() {
    activeValidators = LRUCache.create(MAX_ACTIVE_VALIDATORS_CACHE);
    beaconProposerIndex = LRUCache.create(MAX_BEACON_PROPOSER_INDEX_CACHE);
    beaconCommittee = LRUCache.create(MAX_BEACON_COMMITTEE_CACHE);
    attestersTotalBalance = LRUCache.create(MAX_BEACON_COMMITTEE_CACHE);
    totalActiveBalance = LRUCache.create(MAX_TOTAL_ACTIVE_BALANCE_CACHE);
    validatorsPubKeys = LRUCache.create(Integer.MAX_VALUE - 1);
    validatorIndexCache = new ValidatorIndexCache();
    committeeShuffle = LRUCache.create(MAX_COMMITTEE_SHUFFLE_CACHE);
    effectiveBalances = LRUCache.create(MAX_EFFECTIVE_BALANCE_CACHE);
    syncCommitteeCache = LRUCache.create(MAX_SYNC_COMMITTEE_CACHE);
    baseRewardPerIncrement = LRUCache.create(MAX_BASE_REWARD_PER_INCREMENT_CACHE);
  }

  private TransitionCaches(
      Cache<UInt64, IntList> activeValidators,
      Cache<UInt64, Integer> beaconProposerIndex,
      Cache<TekuPair<UInt64, UInt64>, IntList> beaconCommittee,
      Cache<UInt64, UInt64> attestersTotalBalance,
      Cache<UInt64, UInt64> totalActiveBalance,
      Cache<UInt64, BLSPublicKey> validatorsPubKeys,
      ValidatorIndexCache validatorIndexCache,
      Cache<Bytes32, IntList> committeeShuffle,
      Cache<UInt64, List<UInt64>> effectiveBalances,
      Cache<UInt64, Map<UInt64, SyncSubcommitteeAssignments>> syncCommitteeCache,
      Cache<UInt64, UInt64> baseRewardPerIncrement) {
    this.activeValidators = activeValidators;
    this.beaconProposerIndex = beaconProposerIndex;
    this.beaconCommittee = beaconCommittee;
    this.attestersTotalBalance = attestersTotalBalance;
    this.totalActiveBalance = totalActiveBalance;
    this.validatorsPubKeys = validatorsPubKeys;
    this.validatorIndexCache = validatorIndexCache;
    this.committeeShuffle = committeeShuffle;
    this.effectiveBalances = effectiveBalances;
    this.syncCommitteeCache = syncCommitteeCache;
    this.baseRewardPerIncrement = baseRewardPerIncrement;
  }

  public void setLatestTotalBalances(TotalBalances totalBalances) {
    this.latestTotalBalances = Optional.of(totalBalances);
  }

  public Optional<TotalBalances> getLatestTotalBalances() {
    return latestTotalBalances;
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
   * <p>WARNING: May contain mappings for public keys of validators that are not yet registered in
   * this state (but when registered are guaranteed to be at that index). Check index < total
   * validator count before looking up the cache
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
   * Makes an independent copy which contains all the data in this instance Modifications to
   * returned caches shouldn't affect caches from this instance
   */
  public TransitionCaches copy() {
    return new TransitionCaches(
        activeValidators.copy(),
        beaconProposerIndex.copy(),
        beaconCommittee.copy(),
        attestersTotalBalance.copy(),
        totalActiveBalance.copy(),
        validatorsPubKeys,
        validatorIndexCache,
        committeeShuffle.copy(),
        effectiveBalances.copy(),
        syncCommitteeCache.copy(),
        baseRewardPerIncrement.copy());
  }
}

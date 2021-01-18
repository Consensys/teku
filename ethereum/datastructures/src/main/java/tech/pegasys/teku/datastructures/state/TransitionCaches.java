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

package tech.pegasys.teku.datastructures.state;

import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.independent.TotalBalances;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.collections.cache.NoOpCache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** The container class for all transition caches. */
public class TransitionCaches {

  private static final int MAX_ACTIVE_VALIDATORS_CACHE = 8;
  private static final int MAX_BEACON_PROPOSER_INDEX_CACHE = 1;
  private static final int MAX_BEACON_COMMITTEE_CACHE = 64 * 64;
  private static final int MAX_COMMITTEE_SHUFFLE_CACHE = 2;

  private static final TransitionCaches NO_OP_INSTANCE =
      new TransitionCaches(
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
          ValidatorIndexCache.NO_OP_INSTANCE,
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

  private final Cache<UInt64, List<Integer>> activeValidators;
  private final Cache<UInt64, Integer> beaconProposerIndex;
  private final Cache<Pair<UInt64, UInt64>, List<Integer>> beaconCommittee;
  private final Cache<UInt64, BLSPublicKey> validatorsPubKeys;
  private final ValidatorIndexCache validatorIndexCache;
  private final Cache<Bytes32, List<Integer>> committeeShuffle;

  private volatile Optional<TotalBalances> latestTotalBalances = Optional.empty();

  private TransitionCaches() {
    activeValidators = new LRUCache<>(MAX_ACTIVE_VALIDATORS_CACHE);
    beaconProposerIndex = new LRUCache<>(MAX_BEACON_PROPOSER_INDEX_CACHE);
    beaconCommittee = new LRUCache<>(MAX_BEACON_COMMITTEE_CACHE);
    validatorsPubKeys = new LRUCache<>(Integer.MAX_VALUE - 1);
    validatorIndexCache = new ValidatorIndexCache();
    committeeShuffle = new LRUCache<>(MAX_COMMITTEE_SHUFFLE_CACHE);
  }

  private TransitionCaches(
      Cache<UInt64, List<Integer>> activeValidators,
      Cache<UInt64, Integer> beaconProposerIndex,
      Cache<Pair<UInt64, UInt64>, List<Integer>> beaconCommittee,
      Cache<UInt64, BLSPublicKey> validatorsPubKeys,
      ValidatorIndexCache validatorIndexCache,
      Cache<Bytes32, List<Integer>> committeeShuffle) {
    this.activeValidators = activeValidators;
    this.beaconProposerIndex = beaconProposerIndex;
    this.beaconCommittee = beaconCommittee;
    this.validatorsPubKeys = validatorsPubKeys;
    this.validatorIndexCache = validatorIndexCache;
    this.committeeShuffle = committeeShuffle;
  }

  public void setLatestTotalBalances(TotalBalances totalBalances) {
    this.latestTotalBalances = Optional.of(totalBalances);
  }

  public Optional<TotalBalances> getLatestTotalBalances() {
    return latestTotalBalances;
  }

  /** (epoch) -> (active validators) cache */
  public Cache<UInt64, List<Integer>> getActiveValidators() {
    return activeValidators;
  }

  /** (slot) -> (beacon proposer index) cache */
  public Cache<UInt64, Integer> getBeaconProposerIndex() {
    return beaconProposerIndex;
  }

  /** (slot, committeeIndex) -> (committee) cache */
  public Cache<Pair<UInt64, UInt64>, List<Integer>> getBeaconCommittee() {
    return beaconCommittee;
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
  public Cache<Bytes32, List<Integer>> getCommitteeShuffle() {
    return committeeShuffle;
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
        validatorsPubKeys,
        validatorIndexCache,
        committeeShuffle.copy());
  }
}

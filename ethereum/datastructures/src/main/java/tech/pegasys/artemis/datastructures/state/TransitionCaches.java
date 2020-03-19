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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.cache.Cache;
import tech.pegasys.artemis.util.cache.LRUCache;
import tech.pegasys.artemis.util.cache.NoOpCache;

/** The container class for all transition caches. */
public class TransitionCaches {

  private static int MAX_ACTIVE_VALIDATORS_CACHE = 8;
  private static int MAX_BEACON_PROPOSER_INDEX_CACHE = 1;
  private static int MAX_BEACON_COMMITTEE_CACHE = 64 * 64;
  private static int MAX_TOTAL_ACTIVE_BALANCE_CACHE = 1;
  private static int MAX_COMMITTEE_SHUFFLE_CACHE = 2;

  private static final TransitionCaches NO_OP_INSTANCE =
      new TransitionCaches(
          NoOpCache.getNoOpCache(),
          NoOpCache.getNoOpCache(),
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

  private final Cache<UnsignedLong, List<Integer>> activeValidators;
  private final Cache<UnsignedLong, Integer> beaconProposerIndex;
  private final Cache<Pair<UnsignedLong, UnsignedLong>, List<Integer>> beaconCommittee;
  private final Cache<UnsignedLong, UnsignedLong> totalActiveBalance;
  private final Cache<UnsignedLong, BLSPublicKey> validatorsPubKeys;
  private final Cache<Bytes32, List<Integer>> committeeShuffle;

  private TransitionCaches() {
    activeValidators = new LRUCache<>(MAX_ACTIVE_VALIDATORS_CACHE);
    beaconProposerIndex = new LRUCache<>(MAX_BEACON_PROPOSER_INDEX_CACHE);
    beaconCommittee = new LRUCache<>(MAX_BEACON_COMMITTEE_CACHE);
    totalActiveBalance = new LRUCache<>(MAX_TOTAL_ACTIVE_BALANCE_CACHE);
    validatorsPubKeys = new LRUCache<>(Integer.MAX_VALUE - 1);
    committeeShuffle = new LRUCache<>(MAX_COMMITTEE_SHUFFLE_CACHE);
  }

  public TransitionCaches(
      Cache<UnsignedLong, List<Integer>> activeValidators,
      Cache<UnsignedLong, Integer> beaconProposerIndex,
      Cache<Pair<UnsignedLong, UnsignedLong>, List<Integer>> beaconCommittee,
      Cache<UnsignedLong, UnsignedLong> totalActiveBalance,
      Cache<UnsignedLong, BLSPublicKey> validatorsPubKeys,
      Cache<Bytes32, List<Integer>> committeeShuffle) {
    this.activeValidators = activeValidators;
    this.beaconProposerIndex = beaconProposerIndex;
    this.beaconCommittee = beaconCommittee;
    this.totalActiveBalance = totalActiveBalance;
    this.validatorsPubKeys = validatorsPubKeys;
    this.committeeShuffle = committeeShuffle;
  }

  /** (epoch) -> (active validators) cache */
  public Cache<UnsignedLong, List<Integer>> getActiveValidators() {
    return activeValidators;
  }

  /** (slot) -> (beacon proposer index) cache */
  public Cache<UnsignedLong, Integer> getBeaconProposerIndex() {
    return beaconProposerIndex;
  }

  /** (slot, committeeIndex) -> (committee) cache */
  public Cache<Pair<UnsignedLong, UnsignedLong>, List<Integer>> getBeaconCommittee() {
    return beaconCommittee;
  }

  /** (epoch) -> (total active balance) cache */
  public Cache<UnsignedLong, UnsignedLong> getTotalActiveBalance() {
    return totalActiveBalance;
  }

  /** (validator index) -> (validator pub key) cache */
  public Cache<UnsignedLong, BLSPublicKey> getValidatorsPubKeys() {
    return validatorsPubKeys;
  }

  /** (epoch committee seed) -> (validators shuffle for epoch) cache */
  public Cache<Bytes32, List<Integer>> getCommitteeShuffle() {
    return committeeShuffle;
  }

  public void invalidate() {
    activeValidators.clear();
    beaconProposerIndex.clear();
    beaconCommittee.clear();
    totalActiveBalance.clear();
    validatorsPubKeys.clear();
    committeeShuffle.clear();
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
        totalActiveBalance.copy(),
        validatorsPubKeys,
        committeeShuffle.copy());
  }
}

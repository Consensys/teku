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
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.CaffeineCache;
import tech.pegasys.teku.infrastructure.collections.cache.NoOpCache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * A container for caches that are safe to be shared across all BeaconState instances. This includes
 * caches for data that is effectively immutable or append-only, like the validator registry.
 */
public class SharedBeaconStateCaches {

  public static final SharedBeaconStateCaches DEFAULT_INSTANCE =
      new SharedBeaconStateCaches(
          CaffeineCache.create(Integer.MAX_VALUE - 1), new ValidatorIndexCache());
  public static final SharedBeaconStateCaches NO_OP_INSTANCE =
      new SharedBeaconStateCaches(NoOpCache.getNoOpCache(), ValidatorIndexCache.NO_OP_INSTANCE);

  private final Cache<UInt64, BLSPublicKey> validatorsPubKeys;
  private final ValidatorIndexCache validatorIndexCache;

  private SharedBeaconStateCaches(
      final Cache<UInt64, BLSPublicKey> validatorsPubKeys,
      final ValidatorIndexCache validatorIndexCache) {
    this.validatorsPubKeys = validatorsPubKeys;
    this.validatorIndexCache = validatorIndexCache;
  }

  public static SharedBeaconStateCaches get() {
    return DEFAULT_INSTANCE;
  }

  public Cache<UInt64, BLSPublicKey> getValidatorsPubKeys() {
    return validatorsPubKeys;
  }

  public ValidatorIndexCache getValidatorIndexCache() {
    return validatorIndexCache;
  }

  @VisibleForTesting
  public void clear() {
    validatorsPubKeys.clear();
    validatorIndexCache.clear();
  }
}

/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.validatorcache;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;

public class ActiveValidatorCache implements ActiveValidatorChannel {
  private final Spec spec;
  private static final Logger LOG = LogManager.getLogger();
  private static final int VALIDATOR_CACHE_SPARE_CAPACITY = 1000;
  // validatorActiveEpochs is indexed by validator id, contains mod(3) of epoch values - basically
  // rotating list
  private UInt64[][] validatorActiveEpochs;

  public ActiveValidatorCache(final Spec spec, final int initialSize) {
    Preconditions.checkArgument(initialSize >= 0);
    validatorActiveEpochs = new UInt64[initialSize + VALIDATOR_CACHE_SPARE_CAPACITY][];
    this.spec = spec;
  }

  void touch(final UInt64 validatorIndex, final UInt64 epoch) {
    Preconditions.checkNotNull(validatorIndex);
    Preconditions.checkNotNull(epoch);
    LOG.trace("Touch validator {} at epoch {}", validatorIndex, epoch);

    if (validatorIndex.intValue() >= validatorActiveEpochs.length) {
      grow(validatorIndex);
    }
    ensureNodeIsInitialized(validatorIndex);
    validatorActiveEpochs[validatorIndex.intValue()][epoch.mod(3).intValue()] = epoch;
  }

  boolean isValidatorSeenAtEpoch(final UInt64 validatorIndex, final UInt64 epoch) {
    Preconditions.checkNotNull(validatorIndex);
    Preconditions.checkNotNull(epoch);

    if (validatorActiveEpochs.length <= validatorIndex.intValue()) {
      LOG.debug(
          "validator index {} exceeds array size {}", validatorIndex, validatorActiveEpochs.length);
      return false;
    }

    return validatorActiveEpochs[validatorIndex.intValue()] != null
        && epoch.equals(validatorActiveEpochs[validatorIndex.intValue()][epoch.mod(3).intValue()]);
  }

  int getCacheSize() {
    return validatorActiveEpochs.length;
  }

  @VisibleForTesting
  UInt64[] getValidatorEpochs(final UInt64 validatorIndex) {
    Preconditions.checkArgument(validatorIndex.intValue() < validatorActiveEpochs.length);
    return validatorActiveEpochs[validatorIndex.intValue()];
  }

  private void ensureNodeIsInitialized(final UInt64 validatorIndex) {
    if (validatorActiveEpochs[validatorIndex.intValue()] == null) {
      validatorActiveEpochs[validatorIndex.intValue()] = new UInt64[3];
    }
  }

  private void grow(final UInt64 requestedValidatorIndex) {
    final int newSize =
        requestedValidatorIndex.intValue()
                < validatorActiveEpochs.length + VALIDATOR_CACHE_SPARE_CAPACITY
            ? validatorActiveEpochs.length + VALIDATOR_CACHE_SPARE_CAPACITY
            : requestedValidatorIndex.plus(VALIDATOR_CACHE_SPARE_CAPACITY).intValue();
    LOG.trace(
        "Growing ActiveValidatorCache from {} to {} elements",
        validatorActiveEpochs.length,
        newSize);
    validatorActiveEpochs = Arrays.copyOf(validatorActiveEpochs, newSize);
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block) {
    touch(block.getProposerIndex(), spec.computeEpochAtSlot(block.getSlot()));
  }

  @Override
  public void onAttestation(final ValidateableAttestation validateableAttestation) {
    if (validateableAttestation.getIndexedAttestation().isPresent()) {
      final IndexedAttestation attestation = validateableAttestation.getIndexedAttestation().get();
      final UInt64 epoch = spec.computeEpochAtSlot(attestation.getData().getSlot());
      attestation
          .getAttesting_indices()
          .forEach((validatorIndex) -> touch(validatorIndex.get(), epoch));
    }
  }

  @Override
  public SafeFuture<Map<UInt64, Boolean>> validatorsLiveAtEpoch(
      final List<UInt64> validators, final UInt64 epoch) {
    final Map<UInt64, Boolean> result = new HashMap<>();
    for (UInt64 validator : validators) {
      result.put(validator, isValidatorSeenAtEpoch(validator, epoch));
    }

    return SafeFuture.completedFuture(result);
  }
}

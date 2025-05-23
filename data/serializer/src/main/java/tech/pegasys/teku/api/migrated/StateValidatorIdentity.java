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

package tech.pegasys.teku.api.migrated;

import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public record StateValidatorIdentity(UInt64 index, BLSPublicKey publicKey, UInt64 activationEpoch) {

  public static Optional<StateValidatorIdentity> fromState(
      final BeaconState state, final Integer index) {
    if (index >= state.getValidators().size()) {
      return Optional.empty();
    }
    final Validator validator = state.getValidators().get(index);
    return Optional.of(
        new StateValidatorIdentity(
            UInt64.valueOf(index), validator.getPublicKey(), validator.getActivationEpoch()));
  }
}

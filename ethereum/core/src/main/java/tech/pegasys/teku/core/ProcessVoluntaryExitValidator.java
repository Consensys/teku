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

package tech.pegasys.teku.core;

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_committee_count_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.is_active_validator;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_VOLUNTARY_EXIT;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.util.config.Constants.PERSISTENT_COMMITTEE_PERIOD;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateCache;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.util.config.Constants;

public class ProcessVoluntaryExitValidator {

  public Optional<InvalidReason> validateExit(
          final BeaconState state, final SignedVoluntaryExit signedExit, final BLSSignatureVerifier signatureVerifier) {
    VoluntaryExit exit = signedExit.getMessage();
    return firstOf(
            () ->
                    check(
                            UnsignedLong.valueOf(state.getValidators().size()).compareTo(exit.getValidator_index()) > 0,
                            InvalidReason.INVALID_VALIDATOR_INDEX),
            () ->
                    check(
                            is_active_validator(getValidator(state, exit), get_current_epoch(state)),
                            InvalidReason.VALIDATOR_INACTIVE),
            () ->
                    check(
                           getValidator(state, exit).getExit_epoch().compareTo(FAR_FUTURE_EPOCH) == 0,
                            InvalidReason.EXIT_INITIATED),
            () ->
                    check(
                            get_current_epoch(state).compareTo(exit.getEpoch()) >= 0,
                            InvalidReason.SUBMITTED_TOO_EARLY),
            () ->
                    check(
                            get_current_epoch(state)
                                    .compareTo(getValidator(state, exit)
                                                    .getActivation_epoch()
                                                    .plus(UnsignedLong.valueOf(PERSISTENT_COMMITTEE_PERIOD)))
                            InvalidReason.VALIDATOR_TOO_YOUNG),
            () -> {
              BLSPublicKey publicKey =
                      BeaconStateCache.getTransitionCaches(state)
                              .getValidatorsPubKeys()
                              .get(
                                      exit.getValidator_index(),
                                      idx -> state.getValidators().get(toIntExact(idx.longValue())).getPubkey());
              final Bytes domain = get_domain(state, DOMAIN_VOLUNTARY_EXIT, exit.getEpoch());
              final Bytes signing_root = compute_signing_root(exit, domain);
              return check(signatureVerifier.verify(
                      publicKey,
                      signing_root,
                      signedExit.getSignature()),
                      InvalidReason.INVALID_SIGNATURE);
            }
    );
  }

  private Validator getValidator(BeaconState state, VoluntaryExit exit) {
    return state.getValidators().get(toIntExact(exit.getValidator_index().longValue()));
  }

  @SafeVarargs
  private Optional<InvalidReason> firstOf(final Supplier<Optional<InvalidReason>>... checks) {
    return Stream.of(checks)
            .map(Supplier::get)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
  }

  @CheckReturnValue
  private Optional<InvalidReason> check(final boolean isValid, final InvalidReason check) {
    return !isValid ? Optional.of(check) : Optional.empty();
  }

  public enum InvalidReason {
    INVALID_VALIDATOR_INDEX("Invalid validator index"),
    VALIDATOR_INACTIVE("Validator is not active"),
    EXIT_INITIATED("Validator has already initiated exit"),
    SUBMITTED_TOO_EARLY("Specified exit epoch is still in the future"),
    VALIDATOR_TOO_YOUNG("Validator has not been active long enough"),
    INVALID_SIGNATURE("Exit signature is invalid");

    private final String description;

    InvalidReason(final String description) {
      this.description = description;
    }

    public String describe() {
      return description;
    }
  }
}

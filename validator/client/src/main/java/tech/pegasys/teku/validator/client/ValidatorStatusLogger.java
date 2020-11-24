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

package tech.pegasys.teku.validator.client;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class ValidatorStatusLogger {

  private static final Logger LOG = LogManager.getLogger();

  final Set<BLSPublicKey> validatorPublicKeys;
  final ValidatorApiChannel validatorApiChannel;
  final AtomicReference<Map<BLSPublicKey, ValidatorStatus>> latestValidatorStatuses =
      new AtomicReference<>();

  public ValidatorStatusLogger(
      Set<BLSPublicKey> validatorPublicKeys, ValidatorApiChannel validatorApiChannel) {
    this.validatorPublicKeys = validatorPublicKeys;
    this.validatorApiChannel = validatorApiChannel;
  }

  public void printInitialValidatorStatuses() {
    validatorApiChannel
        .getValidatorStatuses(getAsIdentifiers(validatorPublicKeys))
        .thenAccept(
            maybeValidatorStatuses -> {
              if (maybeValidatorStatuses.isEmpty()) {
                LOG.error("Unable to retrieve validator statuses from BeaconNode.");
                return;
              }

              Map<BLSPublicKey, ValidatorStatus> validatorStatuses = maybeValidatorStatuses.get();
              for (BLSPublicKey publicKey : validatorPublicKeys) {
                Optional<ValidatorStatus> maybeValidatorStatus =
                    Optional.ofNullable(validatorStatuses.get(publicKey));
                maybeValidatorStatus.ifPresentOrElse(
                    validatorStatus ->
                        LOG.info(
                            "Validator {} status is " + validatorStatus,
                            publicKey.toAbbreviatedString()),
                    () -> LOG.info("Error retrieving status for validator {}", publicKey));
              }
            })
        .reportExceptions();
  }

  public void checkValidatorStatusChanges() {
    validatorApiChannel
        .getValidatorStatuses(getAsIdentifiers(validatorPublicKeys))
        .thenAccept(
            maybeNewValidatorStatuses -> {
              if (maybeNewValidatorStatuses.isEmpty()) {
                LOG.error("Unable to retrieve validator statuses from BeaconNode.");
                return;
              }

              Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses =
                  maybeNewValidatorStatuses.get();

              Map<BLSPublicKey, ValidatorStatus> oldValidatorStatuses =
                  latestValidatorStatuses.getAndSet(newValidatorStatuses);
              if (oldValidatorStatuses == null) {
                return;
              }

              for (BLSPublicKey key : oldValidatorStatuses.keySet()) {
                ValidatorStatus oldStatus = oldValidatorStatuses.get(key);
                ValidatorStatus newStatus = newValidatorStatuses.get(key);
                if (oldStatus.equals(newStatus)) {
                  continue;
                }

                LOG.warn(
                    "Validator {} has changed status from " + oldStatus + " to " + newStatus,
                    key::toAbbreviatedString);
              }
            })
        .reportExceptions();
  }

  private static List<String> getAsIdentifiers(Set<BLSPublicKey> validatorPublicKeys) {
    return validatorPublicKeys.stream().map(BLSPublicKey::toString).collect(toList());
  }
}

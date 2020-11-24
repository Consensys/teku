package tech.pegasys.teku.validator.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.toList;

public class ValidatorStatusLogger {

  private static final Logger LOG = LogManager.getLogger();

  final Set<BLSPublicKey> validatorPublicKeys;
  final ValidatorApiChannel validatorApiChannel;
  final AtomicReference<Map<BLSPublicKey, ValidatorStatus>> latestValidatorStatuses = new AtomicReference<>();

  public ValidatorStatusLogger(Set<BLSPublicKey> validatorPublicKeys, ValidatorApiChannel validatorApiChannel) {
    this.validatorPublicKeys = validatorPublicKeys;
    this.validatorApiChannel = validatorApiChannel;
  }

  public void printInitialValidatorStatuses() {
    validatorApiChannel.getValidatorStatuses(getAsIdentifiers(validatorPublicKeys))
            .thenAccept(maybeValidatorStatuses -> {

              if (maybeValidatorStatuses.isEmpty()) {
                LOG.error("Unable to retrieve validator statuses from BeaconNode.");
                return;
              }

              Map<BLSPublicKey, ValidatorStatus> validatorStatuses = maybeValidatorStatuses.get();
              for (BLSPublicKey publicKey : validatorPublicKeys) {
                Optional<ValidatorStatus> maybeValidatorStatus = Optional.ofNullable(validatorStatuses.get(publicKey));
                maybeValidatorStatus.ifPresentOrElse(
                        validatorStatus -> LOG.info("Validator {} status is " + validatorStatus, publicKey.toAbbreviatedString()),
                        () -> LOG.info("Error retrieving status for validator {}", publicKey)
                );
              }
            })
            .reportExceptions();
  }

  public void checkValidatorStatusChanges() {
    validatorApiChannel.getValidatorStatuses(getAsIdentifiers(validatorPublicKeys))
            .thenAccept(maybeNewValidatorStatuses -> {

              if (maybeNewValidatorStatuses.isEmpty()) {
                LOG.error("Unable to retrieve validator statuses from BeaconNode.");
                return;
              }

              Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses = maybeNewValidatorStatuses.get();

              Map<BLSPublicKey, ValidatorStatus> oldValidatorStatuses = latestValidatorStatuses.getAndSet(newValidatorStatuses);
              if (oldValidatorStatuses == null) {
                return;
              }

              for (BLSPublicKey key : oldValidatorStatuses.keySet()) {
                ValidatorStatus oldStatus = oldValidatorStatuses.get(key);
                ValidatorStatus newStatus = newValidatorStatuses.get(key);
                if (oldStatus.equals(newStatus)) {
                  continue;
                }

                LOG.warn("Validator {} has changed status from " + oldStatus + " to " + newStatus, key::toAbbreviatedString);
              }
            })
            .reportExceptions();
  }

  private static List<String> getAsIdentifiers(Set<BLSPublicKey> validatorPublicKeys) {
    return validatorPublicKeys.stream().map(BLSPublicKey::toString).collect(toList());
  }
}

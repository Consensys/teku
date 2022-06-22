/*
 * Copyright ConsenSys Software Inc., 2022
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

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszUtils;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class ValidatorRegistrationBatchSender {
  private static final Logger LOG = LogManager.getLogger();

  private final int batchSize;
  private final ValidatorApiChannel validatorApiChannel;

  public ValidatorRegistrationBatchSender(
      final int batchSize, final ValidatorApiChannel validatorApiChannel) {
    this.batchSize = batchSize;
    this.validatorApiChannel = validatorApiChannel;
  }

  public SafeFuture<Void> sendInBatches(
      final List<SignedValidatorRegistration> validatorRegistrations) {
    if (validatorRegistrations.isEmpty()) {
      LOG.debug("No validator(s) registrations required to be sent.");
      return SafeFuture.completedFuture(null);
    }

    final List<List<SignedValidatorRegistration>> batchedRegistrations =
        Lists.partition(validatorRegistrations, batchSize);

    final AtomicInteger processedBatches = new AtomicInteger(0);
    final AtomicInteger successfullySentRegistrations = new AtomicInteger(0);

    LOG.debug(
        "Going to send {} validator(s) registrations to Beacon Node in {} batches",
        validatorRegistrations.size(),
        batchedRegistrations.size());

    final Iterator<List<SignedValidatorRegistration>> batchedRegistrationsIterator =
        batchedRegistrations.iterator();

    return SafeFuture.asyncDoWhile(
            () -> {
              if (!batchedRegistrationsIterator.hasNext()) {
                return SafeFuture.completedFuture(false);
              }
              final List<SignedValidatorRegistration> batch = batchedRegistrationsIterator.next();
              return sendBatch(batch)
                  .thenApply(
                      __ -> {
                        LOG.debug(
                            "Batch {}/{} -> {} validator(s) registrations were sent to the Beacon Node.",
                            processedBatches.incrementAndGet(),
                            batchedRegistrations.size(),
                            batch.size());
                        successfullySentRegistrations.updateAndGet(count -> count + batch.size());
                        return true;
                      });
            })
        .whenComplete(
            (__, throwable) ->
                LOG.info(
                    "{} out of {} validator(s) registrations were successfully sent to the Beacon Node.",
                    successfullySentRegistrations.get(),
                    validatorRegistrations.size()));
  }

  private SafeFuture<Void> sendBatch(
      final List<SignedValidatorRegistration> validatorRegistrations) {
    final SszList<SignedValidatorRegistration> sszValidatorRegistrations =
        SszUtils.toSszList(
            ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA, validatorRegistrations);
    return validatorApiChannel.registerValidators(sszValidatorRegistrations);
  }
}

/*
 * Copyright 2022 ConsenSys AG.
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

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.impl.SszUtils;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class ValidatorRegistrator implements ValidatorTimingChannel {

  private final Map<ValidatorIdentity, SignedValidatorRegistration> cachedValidatorRegistrations =
      Maps.newConcurrentMap();

  private final AtomicBoolean firstCallDone = new AtomicBoolean(false);

  private final ForkProvider forkProvider;
  private final Spec spec;
  private final TimeProvider timeProvider;
  private final OwnedValidators ownedValidators;
  private final ValidatorApiChannel validatorApiChannel;

  public ValidatorRegistrator(
      final ForkProvider forkProvider,
      final OwnedValidators ownedValidators,
      final ValidatorApiChannel validatorApiChannel,
      final Spec spec,
      final TimeProvider timeProvider) {
    this.forkProvider = forkProvider;
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.ownedValidators = ownedValidators;
    this.validatorApiChannel = validatorApiChannel;
  }

  @Override
  public void onSlot(UInt64 slot) {
    if (isBeginningOfEpoch(slot) || firstCallDone.compareAndSet(false, true)) {
      forkProvider
          .getForkInfo(slot)
          .thenApply(
              forkInfo -> {
                final UInt64 epoch = spec.computeEpochAtSlot(slot);
                return registerValidators(epoch, forkInfo);
              })
          .finish(VALIDATOR_LOGGER::registeringValidatorsFailed);
    }
  }

  @Override
  public void onHeadUpdate(
      UInt64 slot,
      Bytes32 previousDutyDependentRoot,
      Bytes32 currentDutyDependentRoot,
      Bytes32 headBlockRoot) {}

  @Override
  public void onPossibleMissedEvents() {}

  @Override
  public void onValidatorsAdded() {}

  @Override
  public void onBlockProductionDue(UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(UInt64 slot) {}

  private boolean isBeginningOfEpoch(final UInt64 slot) {
    return slot.mod(spec.getSlotsPerEpoch(slot)).isZero();
  }

  private SafeFuture<Void> registerValidators(final UInt64 epoch, final ForkInfo forkInfo) {

    final Stream<SafeFuture<SignedValidatorRegistration>> validatorRegistrationsFutures =
        ownedValidators.getActiveValidators().stream()
            .map(
                validator -> {
                  // hardcoding fee_recipient and gas_limit to ZERO for now. The real values will be
                  // passed in a future PR.
                  final ValidatorIdentity validatorIdentity =
                      new ValidatorIdentity(Bytes20.ZERO, UInt64.ZERO, validator.getPublicKey());

                  if (cachedValidatorRegistrations.containsKey(validatorIdentity)) {
                    return SafeFuture.completedFuture(
                        cachedValidatorRegistrations.get(validatorIdentity));
                  }

                  final ValidatorRegistration validatorRegistration =
                      createValidatorRegistration(validatorIdentity);
                  final Signer signer = validator.getSigner();
                  return signValidatorRegistration(validatorRegistration, signer, epoch, forkInfo)
                      .thenPeek(
                          signedValidatorRegistration ->
                              cachedValidatorRegistrations.put(
                                  validatorIdentity, signedValidatorRegistration));
                });

    return SafeFuture.collectAll(validatorRegistrationsFutures)
        .thenApply(
            validatorRegistrations ->
                SszUtils.toSszList(
                    ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA, validatorRegistrations))
        .thenCompose(validatorApiChannel::registerValidators);
  }

  private ValidatorRegistration createValidatorRegistration(
      final ValidatorIdentity validatorIdentity) {
    return ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA.create(
        validatorIdentity.getFeeRecipient(),
        validatorIdentity.getGasLimit(),
        timeProvider.getTimeInSeconds(),
        validatorIdentity.getPublicKey());
  }

  private SafeFuture<SignedValidatorRegistration> signValidatorRegistration(
      final ValidatorRegistration validatorRegistration,
      final Signer signer,
      final UInt64 epoch,
      final ForkInfo forkInfo) {
    return signer
        .signValidatorRegistration(validatorRegistration, epoch, forkInfo)
        .thenApply(
            signature ->
                ApiSchemas.SIGNED_VALIDATOR_REGISTRATION_SCHEMA.create(
                    validatorRegistration, signature));
  }
}

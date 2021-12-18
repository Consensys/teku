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

package tech.pegasys.teku.validator.client;

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.datastructures.operations.versions.merge.BeaconPreparableProposer;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class BeaconProposerPreparer implements ValidatorTimingChannel {
  private final ValidatorApiChannel validatorApiChannel;
  private final ValidatorIndexProvider validatorIndexProvider;
  private final OwnedValidators validators;
  private final Spec spec;
  private final Optional<Eth1Address> feeRecipient;
  private boolean firstCallDone = false;

  public BeaconProposerPreparer(
      ValidatorApiChannel validatorApiChannel,
      ValidatorIndexProvider validatorIndexProvider,
      Optional<Eth1Address> feeRecipient,
      OwnedValidators validators,
      Spec spec) {
    this.validatorApiChannel = validatorApiChannel;
    this.validatorIndexProvider = validatorIndexProvider;
    this.validators = validators;
    this.feeRecipient = feeRecipient;
    this.spec = spec;
  }

  @Override
  public void onSlot(UInt64 slot) {
    if (slot.mod(spec.getSlotsPerEpoch(slot)).isZero() || !firstCallDone) {
      firstCallDone = true;
      validatorIndexProvider
          .getValidatorIndices(validators.getPublicKeys())
          .thenApply(
              integers ->
                  integers.stream()
                      .map(
                          index ->
                              new BeaconPreparableProposer(
                                  UInt64.valueOf(index), getFeeRecipient()))
                      .collect(Collectors.toList()))
          .thenAccept(validatorApiChannel::prepareBeaconProposer)
          .finish(VALIDATOR_LOGGER::beaconProposerPreparationFailed);
    }
  }

  private Eth1Address getFeeRecipient() {
    return feeRecipient.orElseThrow(
        () ->
            new InvalidConfigurationException(
                "Invalid configuration. --Xvalidators-suggested-fee-recipient-address must be specified when Merge milestone is active"));
  }

  @Override
  public void onHeadUpdate(
      UInt64 slot,
      Bytes32 previousDutyDependentRoot,
      Bytes32 currentDutyDependentRoot,
      Bytes32 headBlockRoot) {}

  @Override
  public void onChainReorg(UInt64 newSlot, UInt64 commonAncestorSlot) {}

  @Override
  public void onPossibleMissedEvents() {}

  @Override
  public void onBlockProductionDue(UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(UInt64 slot) {}
}

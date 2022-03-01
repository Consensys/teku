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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.ProposerConfig.Config;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;

public class BeaconProposerPreparer implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorApiChannel validatorApiChannel;
  private final ValidatorIndexProvider validatorIndexProvider;
  private final ProposerConfigProvider proposerConfigProvider;
  private final Spec spec;
  private final Optional<? extends Bytes20> defaultFeeRecipient;
  private boolean firstCallDone = false;

  public BeaconProposerPreparer(
      ValidatorApiChannel validatorApiChannel,
      ValidatorIndexProvider validatorIndexProvider,
      ProposerConfigProvider proposerConfigProvider,
      Optional<? extends Bytes20> defaultFeeRecipient,
      Spec spec) {
    this.validatorApiChannel = validatorApiChannel;
    this.validatorIndexProvider = validatorIndexProvider;
    this.proposerConfigProvider = proposerConfigProvider;
    this.defaultFeeRecipient = defaultFeeRecipient;
    this.spec = spec;
  }

  @Override
  public void onSlot(UInt64 slot) {
    if (slot.mod(spec.getSlotsPerEpoch(slot)).isZero() || !firstCallDone) {
      firstCallDone = true;

      sendPreparableProposerList();
    }
  }

  private void sendPreparableProposerList() {
    SafeFuture<Optional<ProposerConfig>> proposerConfigFuture =
        proposerConfigProvider.getProposerConfig();

    validatorIndexProvider
        .getValidatorIndicesByPublicKey()
        .thenCompose(
            publicKeyToIndex ->
                proposerConfigFuture
                    .thenApply(
                        proposerConfig ->
                            buildBeaconPreparableProposerList(proposerConfig, publicKeyToIndex))
                    .exceptionally(
                        throwable -> {
                          LOG.warn("An error occurred while obtaining proposer config", throwable);
                          return buildBeaconPreparableProposerList(
                              Optional.empty(), publicKeyToIndex);
                        }))
        .thenAccept(validatorApiChannel::prepareBeaconProposer)
        .finish(VALIDATOR_LOGGER::beaconProposerPreparationFailed);
  }

  private List<BeaconPreparableProposer> buildBeaconPreparableProposerList(
      Optional<ProposerConfig> maybeProposerConfig,
      Map<BLSPublicKey, Optional<Integer>> blsPublicKeyToIndexMap) {
    return blsPublicKeyToIndexMap.entrySet().stream()
        .filter(blsPublicKeyOptionalEntry -> blsPublicKeyOptionalEntry.getValue().isPresent())
        .map(
            blsPublicKeyIntegerEntry ->
                new BeaconPreparableProposer(
                    UInt64.valueOf(blsPublicKeyIntegerEntry.getValue().get()),
                    getFeeRecipient(maybeProposerConfig, blsPublicKeyIntegerEntry.getKey())))
        .collect(Collectors.toList());
  }

  private Bytes20 getFeeRecipient(
      Optional<ProposerConfig> maybeProposerConfig, BLSPublicKey blsPublicKey) {
    return maybeProposerConfig
        .flatMap(proposerConfig -> proposerConfig.getConfigForPubKey(blsPublicKey))
        .map(Config::getFeeRecipient)
        .orElseGet(() -> getDefaultFeeRecipient(maybeProposerConfig));
  }

  private Bytes20 getDefaultFeeRecipient(Optional<ProposerConfig> maybeProposerConfig) {
    return maybeProposerConfig
        .flatMap(ProposerConfig::getDefaultConfig)
        .map(Config::getFeeRecipient)
        .or(() -> defaultFeeRecipient)
        .orElseThrow(
            () ->
                new InvalidConfigurationException(
                    "Invalid configuration. --Xvalidators-proposer-default-fee-recipient must be specified when Bellatrix milestone is active"));
  }

  @Override
  public void onHeadUpdate(
      UInt64 slot,
      Bytes32 previousDutyDependentRoot,
      Bytes32 currentDutyDependentRoot,
      Bytes32 headBlockRoot) {}

  @Override
  public void onPossibleMissedEvents() {
    sendPreparableProposerList();
  }

  @Override
  public void onValidatorsAdded() {
    sendPreparableProposerList();
  }

  @Override
  public void onBlockProductionDue(UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(UInt64 slot) {}
}

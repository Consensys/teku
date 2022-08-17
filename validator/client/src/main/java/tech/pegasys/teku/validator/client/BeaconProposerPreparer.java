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

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.ProposerConfig.Config;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;

public class BeaconProposerPreparer
    implements ValidatorTimingChannel, ValidatorRegistrationPropertiesProvider {
  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorApiChannel validatorApiChannel;
  private Optional<ValidatorIndexProvider> validatorIndexProvider;
  private final ProposerConfigProvider proposerConfigProvider;
  private final Optional<Eth1Address> defaultFeeRecipient;
  private final UInt64 defaultGasLimit;
  private final Spec spec;
  private final RuntimeProposerConfig runtimeProposerConfig;

  private Optional<ProposerConfig> maybeProposerConfig = Optional.empty();

  private final AtomicBoolean firstCallDone = new AtomicBoolean(false);
  private final AtomicBoolean sentProposersAtLeastOnce = new AtomicBoolean(false);

  BeaconProposerPreparer(
      ValidatorApiChannel validatorApiChannel,
      ValidatorIndexProvider validatorIndexProvider,
      ProposerConfigProvider proposerConfigProvider,
      Optional<Eth1Address> defaultFeeRecipient,
      UInt64 defaultGasLimit,
      Spec spec) {
    this(
        validatorApiChannel,
        Optional.of(validatorIndexProvider),
        proposerConfigProvider,
        defaultFeeRecipient,
        defaultGasLimit,
        spec,
        Optional.empty());
  }

  public BeaconProposerPreparer(
      ValidatorApiChannel validatorApiChannel,
      Optional<ValidatorIndexProvider> validatorIndexProvider,
      ProposerConfigProvider proposerConfigProvider,
      Optional<Eth1Address> defaultFeeRecipient,
      UInt64 defaultGasLimit,
      Spec spec,
      Optional<Path> mutableProposerConfigPath) {
    this.validatorApiChannel = validatorApiChannel;
    this.validatorIndexProvider = validatorIndexProvider;
    this.proposerConfigProvider = proposerConfigProvider;
    this.defaultFeeRecipient = defaultFeeRecipient;
    this.defaultGasLimit = defaultGasLimit;
    this.spec = spec;
    runtimeProposerConfig = new RuntimeProposerConfig(mutableProposerConfigPath);
  }

  public void initialize(final Optional<ValidatorIndexProvider> provider) {
    this.validatorIndexProvider = provider;
  }

  @Override
  public void onSlot(UInt64 slot) {
    if (validatorIndexProvider.isEmpty()) {
      return;
    }
    if (firstCallDone.compareAndSet(false, true) || isBeginningOfEpoch(slot)) {
      sendPreparableProposerList();
    }
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

  // 2 configurations, 2 defaults
  // Priority order
  // - Specifically configured key in --validator-proposer-config file
  // - proposer set via the SET api (runtime configuration)
  // - default set in --validator-proposer-config file
  // - default set by --validators-proposer-default-fee-recipient
  @Override
  public Optional<Eth1Address> getFeeRecipient(final BLSPublicKey publicKey) {
    if (validatorIndexCannotBeResolved(publicKey)) {
      return Optional.empty();
    }
    return maybeProposerConfig
        .flatMap(config -> getFeeRecipientFromProposerConfig(config, publicKey))
        .or(() -> runtimeProposerConfig.getEth1AddressForPubKey(publicKey))
        .or(
            () ->
                maybeProposerConfig.flatMap(
                    proposerConfig -> proposerConfig.getDefaultConfig().getFeeRecipient()))
        .or(() -> defaultFeeRecipient);
  }

  @Override
  public Optional<UInt64> getGasLimit(BLSPublicKey publicKey) {
    if (validatorIndexCannotBeResolved(publicKey)) {
      return Optional.empty();
    }
    return maybeProposerConfig
        .flatMap(config -> getGasLimitFromProposerConfig(config, publicKey))
        .or(() -> runtimeProposerConfig.getGasLimitForPubKey(publicKey))
        .or(
            () ->
                maybeProposerConfig.flatMap(
                    proposerConfigProvider ->
                        proposerConfigProvider.getDefaultConfig().getGasLimit()))
        .or(() -> Optional.ofNullable(defaultGasLimit));
  }

  @Override
  public boolean isReadyToProvideFeeRecipient() {
    return sentProposersAtLeastOnce.get();
  }

  // Cannot set a fee recipient if the key is specified in the configuration file
  // Cannot set fee recipient to 0x00
  public void setFeeRecipient(final BLSPublicKey publicKey, final Eth1Address eth1Address)
      throws SetFeeRecipientException {
    if (eth1Address.equals(Eth1Address.ZERO)) {
      throw new SetFeeRecipientException("Cannot set fee recipient to 0x00 address.");
    }
    if (validatorIndexCannotBeResolved(publicKey)) {
      throw new SetFeeRecipientException(
          "Validator public key not found when attempting to set fee recipient.");
    }
    Optional<Eth1Address> maybeEth1Address =
        maybeProposerConfig.flatMap(config -> getFeeRecipientFromProposerConfig(config, publicKey));
    if (maybeEth1Address.isPresent()) {
      throw new SetFeeRecipientException("Cannot update fee recipient via api.");
    }
    runtimeProposerConfig.updateFeeRecipient(publicKey, eth1Address);
  }

  public void setGasLimit(final BLSPublicKey publicKey, final UInt64 gasLimit)
      throws SetFeeRecipientException {
    if (validatorIndexCannotBeResolved(publicKey)) {
      throw new SetGasLimitException(
          "Validator public key not found when attempting to set gas limit.");
    }
    Optional<UInt64> maybeGasLimit =
        maybeProposerConfig.flatMap(config -> getGasLimitFromProposerConfig(config, publicKey));
    if (maybeGasLimit.isPresent()) {
      throw new SetGasLimitException("Cannot update gas limit via api.");
    }
    runtimeProposerConfig.updateGasLimit(publicKey, gasLimit);
  }

  public boolean deleteFeeRecipient(final BLSPublicKey publicKey) {
    Optional<Eth1Address> maybeEth1Address =
        maybeProposerConfig.flatMap(config -> getFeeRecipientFromProposerConfig(config, publicKey));
    if (maybeEth1Address.isPresent()) {
      return false;
    }
    runtimeProposerConfig.deleteFeeRecipient(publicKey);
    return true;
  }

  public boolean deleteGasLimit(final BLSPublicKey publicKey) {
    Optional<UInt64> maybeGasLimit =
        maybeProposerConfig.flatMap(config -> getGasLimitFromProposerConfig(config, publicKey));
    if (maybeGasLimit.isPresent()) {
      return false;
    }
    runtimeProposerConfig.deleteGasLimit(publicKey);
    return true;
  }

  private boolean isBeginningOfEpoch(final UInt64 slot) {
    return slot.mod(spec.getSlotsPerEpoch(slot)).isZero();
  }

  private void sendPreparableProposerList() {
    if (validatorIndexProvider.isEmpty()) {
      return;
    }
    SafeFuture<Optional<ProposerConfig>> proposerConfigFuture =
        proposerConfigProvider.getProposerConfig();

    validatorIndexProvider
        .orElseThrow()
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
        .thenCompose(validatorApiChannel::prepareBeaconProposer)
        .finish(
            () -> sentProposersAtLeastOnce.compareAndSet(false, true),
            VALIDATOR_LOGGER::beaconProposerPreparationFailed);
  }

  private Collection<BeaconPreparableProposer> buildBeaconPreparableProposerList(
      Optional<ProposerConfig> maybeProposerConfig,
      Map<BLSPublicKey, Integer> blsPublicKeyToIndexMap) {
    this.maybeProposerConfig = maybeProposerConfig;
    return blsPublicKeyToIndexMap.entrySet().stream()
        .map(
            entry ->
                getFeeRecipient(entry.getKey())
                    .map(
                        eth1Address ->
                            new BeaconPreparableProposer(
                                UInt64.valueOf(entry.getValue()), eth1Address)))
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
  }

  private Optional<Eth1Address> getFeeRecipientFromProposerConfig(
      final ProposerConfig config, final BLSPublicKey publicKey) {
    return config.getConfigForPubKey(publicKey).flatMap(Config::getFeeRecipient);
  }

  private Optional<UInt64> getGasLimitFromProposerConfig(
      final ProposerConfig config, final BLSPublicKey publicKey) {
    return config.getConfigForPubKey(publicKey).flatMap(Config::getGasLimit);
  }

  private boolean validatorIndexCannotBeResolved(final BLSPublicKey publicKey) {
    return validatorIndexProvider.isEmpty()
        || !validatorIndexProvider.get().containsPublicKey(publicKey);
  }
}

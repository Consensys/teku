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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ProposerConfig.BuilderConfig;
import tech.pegasys.teku.validator.client.ProposerConfig.Config;
import tech.pegasys.teku.validator.client.ProposerConfig.RegistrationOverrides;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;

public class ProposerConfigManager implements ProposerConfigPropertiesProvider {
  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorConfig config;
  private final RuntimeProposerConfig runtimeProposerConfig;
  private final ProposerConfigProvider proposerConfigProvider;

  private final AtomicReference<Optional<ProposerConfig>> maybeProposerConfig =
      new AtomicReference<>(Optional.empty());
  private final SafeFuture<Void> initializationComplete = new SafeFuture<>();

  private Optional<OwnedValidators> ownedValidators = Optional.empty();

  public ProposerConfigManager(
      final ValidatorConfig config,
      final RuntimeProposerConfig runtimeProposerConfig,
      final ProposerConfigProvider proposerConfigProvider) {
    checkNotNull(config.getBuilderRegistrationDefaultGasLimit(), "A default Gas Limit is expected");
    this.config = config;
    this.runtimeProposerConfig = runtimeProposerConfig;
    this.proposerConfigProvider = proposerConfigProvider;
  }

  public SafeFuture<Void> initialize(final OwnedValidators ownedValidators) {
    this.ownedValidators = Optional.of(ownedValidators);
    internalRefresh().propagateTo(initializationComplete);
    return initializationComplete;
  }

  @Override
  public SafeFuture<Void> refresh() {
    return internalRefresh()
        .exceptionally(
            throwable -> {
              LOG.warn("An error occurred while obtaining proposer config", throwable);
              return null;
            });
  }

  private SafeFuture<Void> internalRefresh() {
    return proposerConfigProvider.getProposerConfig().thenAccept(maybeProposerConfig::set);
  }

  // Cannot set a fee recipient if the key is specified in the configuration file
  // Cannot set fee recipient to 0x00
  public void setFeeRecipient(final BLSPublicKey publicKey, final Eth1Address eth1Address)
      throws SetFeeRecipientException {
    if (eth1Address.equals(Eth1Address.ZERO)) {
      throw new SetFeeRecipientException("Cannot set fee recipient to 0x00 address.");
    }
    if (!isOwnedValidator(publicKey)) {
      throw new SetFeeRecipientException(
          "Validator public key not found when attempting to set fee recipient.");
    }
    Optional<Eth1Address> maybeEth1Address = getFeeRecipientFromProposerConfig(publicKey);
    if (maybeEth1Address.isPresent()) {
      throw new SetFeeRecipientException("Cannot update fee recipient via api.");
    }
    runtimeProposerConfig.updateFeeRecipient(publicKey, eth1Address);
  }

  public void setGasLimit(final BLSPublicKey publicKey, final UInt64 gasLimit)
      throws SetFeeRecipientException {
    if (!isOwnedValidator(publicKey)) {
      throw new SetGasLimitException(
          "Validator public key not found when attempting to set gas limit.");
    }
    Optional<UInt64> maybeGasLimit = getGasLimitFromProposerConfig(publicKey);
    if (maybeGasLimit.isPresent()) {
      throw new SetGasLimitException("Cannot update gas limit via api.");
    }
    runtimeProposerConfig.updateGasLimit(publicKey, gasLimit);
  }

  public boolean deleteFeeRecipient(final BLSPublicKey publicKey) {
    Optional<Eth1Address> maybeEth1Address = getFeeRecipientFromProposerConfig(publicKey);
    if (maybeEth1Address.isPresent()) {
      return false;
    }
    runtimeProposerConfig.deleteFeeRecipient(publicKey);
    return true;
  }

  public boolean deleteGasLimit(final BLSPublicKey publicKey) {
    Optional<UInt64> maybeGasLimit = getGasLimitFromProposerConfig(publicKey);
    if (maybeGasLimit.isPresent()) {
      return false;
    }
    runtimeProposerConfig.deleteGasLimit(publicKey);
    return true;
  }

  // 2 configurations, 2 defaults
  // Priority order
  // - Specifically configured key in --validator-proposer-config file
  // - proposer set via the SET api (runtime configuration)
  // - default set in --validator-proposer-config file
  // - default set by --validators-proposer-default-fee-recipient
  @Override
  public Optional<Eth1Address> getFeeRecipient(final BLSPublicKey publicKey) {
    return getAttributeWithFallback(Config::getFeeRecipient, publicKey)
        .or(config::getProposerDefaultFeeRecipient);
  }

  @Override
  public UInt64 getGasLimit(final BLSPublicKey publicKey) {
    return getAttributeWithFallback(Config::getBuilderGasLimit, publicKey)
        .orElse(config.getBuilderRegistrationDefaultGasLimit());
  }

  @Override
  public boolean isReadyToProvideProperties() {
    return initializationComplete.isCompletedNormally();
  }

  @Override
  public boolean isBuilderEnabled(final BLSPublicKey publicKey) {
    return getAttributeWithFallback(
            config -> config.getBuilder().flatMap(BuilderConfig::isEnabled), publicKey)
        .orElse(config.isBuilderRegistrationDefaultEnabled());
  }

  @Override
  public Optional<UInt64> getBuilderRegistrationTimestampOverride(final BLSPublicKey publicKey) {
    return getAttributeWithFallback(
            config ->
                config
                    .getBuilder()
                    .flatMap(BuilderConfig::getRegistrationOverrides)
                    .flatMap(RegistrationOverrides::getTimestamp),
            publicKey)
        .or(config::getBuilderRegistrationTimestampOverride);
  }

  @Override
  public Optional<BLSPublicKey> getBuilderRegistrationPublicKeyOverride(
      final BLSPublicKey publicKey) {
    return getAttributeWithFallback(
            config ->
                config
                    .getBuilder()
                    .flatMap(BuilderConfig::getRegistrationOverrides)
                    .flatMap(RegistrationOverrides::getPublicKey),
            publicKey)
        .or(config::getBuilderRegistrationPublicKeyOverride);
  }

  private <T> Optional<T> getAttributeWithFallback(
      final Function<Config, Optional<T>> selector, final BLSPublicKey publicKey) {
    final Optional<ProposerConfig> localMaybeProposerConfig = maybeProposerConfig.get();

    if (localMaybeProposerConfig.isEmpty()) {
      return runtimeProposerConfig.getProposerConfig(publicKey).flatMap(selector);
    }
    return localMaybeProposerConfig
        .get()
        .getConfigForPubKey(publicKey)
        .flatMap(selector)
        .or(() -> runtimeProposerConfig.getProposerConfig(publicKey).flatMap(selector))
        .or(() -> selector.apply(localMaybeProposerConfig.get().getDefaultConfig()));
  }

  private Optional<Eth1Address> getFeeRecipientFromProposerConfig(final BLSPublicKey publicKey) {
    return maybeProposerConfig
        .get()
        .flatMap(config -> config.getConfigForPubKey(publicKey))
        .flatMap(Config::getFeeRecipient);
  }

  private Optional<UInt64> getGasLimitFromProposerConfig(final BLSPublicKey publicKey) {
    return maybeProposerConfig
        .get()
        .flatMap(config -> config.getConfigForPubKey(publicKey))
        .flatMap(Config::getBuilderGasLimit);
  }

  public boolean isOwnedValidator(final BLSPublicKey publicKey) {
    return ownedValidators.isPresent() && ownedValidators.get().getValidator(publicKey).isPresent();
  }
}

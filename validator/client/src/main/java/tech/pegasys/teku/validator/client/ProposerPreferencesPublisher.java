/*
 * Copyright Consensys Software Inc., 2026
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
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.util.ProposerPreferencesUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class ProposerPreferencesPublisher {

  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorApiChannel validatorApiChannel;
  private final OwnedValidators ownedValidators;
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider;
  private final ForkProvider forkProvider;
  private final Spec spec;

  public ProposerPreferencesPublisher(
      final ValidatorApiChannel validatorApiChannel,
      final OwnedValidators ownedValidators,
      final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider,
      final ForkProvider forkProvider,
      final Spec spec) {
    this.validatorApiChannel = validatorApiChannel;
    this.ownedValidators = ownedValidators;
    this.proposerConfigPropertiesProvider = proposerConfigPropertiesProvider;
    this.forkProvider = forkProvider;
    this.spec = spec;
  }

  public void onProposerDutiesLoaded(final UInt64 epoch, final ProposerDuties proposerDuties) {
    if (!spec.isProposerPreferencesAvailableAtEpoch(epoch)) {
      return;
    }

    final List<ProposerDuty> ownedProposerDuties =
        proposerDuties.getDuties().stream()
            .filter(duty -> ownedValidators.hasValidator(duty.getPublicKey()))
            .toList();

    if (ownedProposerDuties.isEmpty()) {
      LOG.debug("No owned validators have proposer duties in epoch {}", epoch);
      return;
    }

    // Gloas's get_proposer_dependent_root(state, e) returns the block root at
    // start_of_(e-1) - 1. For next-epoch duties, BlockProposalUtilFulu's
    // getBlockProposalDependentRoot returns the same value, so we reuse it here.
    final Bytes32 dependentRoot = proposerDuties.getDependentRoot();

    final ProposerPreferencesUtil preferencesUtil = spec.getProposerPreferencesUtil(epoch);

    forkProvider
        .getForkInfo(ownedProposerDuties.getFirst().getSlot())
        .thenCompose(
            forkInfo ->
                SafeFuture.collectAll(
                        ownedProposerDuties.stream()
                            .map(
                                duty ->
                                    createSignedProposerPreferences(
                                        duty, dependentRoot, forkInfo, preferencesUtil)))
                    .thenCompose(
                        signedPreferences -> {
                          final List<SignedProposerPreferences> preferencesList =
                              signedPreferences.stream().flatMap(Optional::stream).toList();
                          if (preferencesList.isEmpty()) {
                            return SafeFuture.COMPLETE;
                          }
                          LOG.debug("Publishing {} proposer preferences", preferencesList.size());
                          return validatorApiChannel
                              .sendSignedProposerPreferences(preferencesList)
                              .thenPeek(
                                  __ ->
                                      LOG.debug(
                                          "Proposer preferences published successfully for {} validators",
                                          preferencesList.size()));
                        }))
        .finish(error -> VALIDATOR_LOGGER.proposerPreferencesPublicationFailed(epoch, error));
  }

  private SafeFuture<Optional<SignedProposerPreferences>> createSignedProposerPreferences(
      final ProposerDuty duty,
      final Bytes32 dependentRoot,
      final ForkInfo forkInfo,
      final ProposerPreferencesUtil preferencesUtil) {
    final Optional<Validator> maybeValidator = ownedValidators.getValidator(duty.getPublicKey());
    if (maybeValidator.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    final Optional<Eth1Address> maybeFeeRecipient =
        proposerConfigPropertiesProvider.getFeeRecipient(duty.getPublicKey());
    if (maybeFeeRecipient.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    final UInt64 gasLimit = proposerConfigPropertiesProvider.getGasLimit(duty.getPublicKey());
    final Optional<ProposerPreferences> maybePreferences =
        preferencesUtil.createProposerPreferences(
            dependentRoot,
            duty.getSlot(),
            UInt64.valueOf(duty.getValidatorIndex()),
            maybeFeeRecipient.get(),
            gasLimit);
    if (maybePreferences.isEmpty()) {
      // Pre-Gloas, the util is NOOP, nothing to publish
      return SafeFuture.completedFuture(Optional.empty());
    }
    final ProposerPreferences preferences = maybePreferences.get();

    return maybeValidator
        .get()
        .getSigner()
        .signProposerPreferences(preferences, forkInfo)
        .thenApply(
            signature -> preferencesUtil.createSignedProposerPreferences(preferences, signature))
        .exceptionally(
            error -> {
              LOG.warn(
                  "Failed to sign proposer preferences for validator {}",
                  duty.getPublicKey(),
                  error);
              return Optional.empty();
            });
  }
}

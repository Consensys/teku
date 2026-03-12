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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class ProposerPreferencesPublisher implements ValidatorTimingChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorApiChannel validatorApiChannel;
  private final OwnedValidators ownedValidators;
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider;
  private final ForkProvider forkProvider;
  private final Spec spec;
  private final AtomicBoolean firstCallDone = new AtomicBoolean(false);

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

  @Override
  public void onSlot(final UInt64 slot) {
    if (firstCallDone.compareAndSet(false, true) || isThirdSlotOfEpoch(slot)) {
      publishProposerPreferences(slot);
    }
  }

  private void publishProposerPreferences(final UInt64 slot) {
    final UInt64 nextEpoch = spec.computeEpochAtSlot(slot).plus(1);
    validatorApiChannel
        .getProposerDuties(nextEpoch, true)
        .thenCompose(
            maybeProposerDuties -> {
              if (maybeProposerDuties.isEmpty()) {
                LOG.debug("No proposer duties available for epoch {}", nextEpoch);
                return SafeFuture.COMPLETE;
              }
              return createAndSendProposerPreferences(maybeProposerDuties.get());
            })
        .finish(
            __ -> LOG.debug("Proposer preferences published successfully for epoch {}", nextEpoch),
            error -> VALIDATOR_LOGGER.proposerPreferencesPublicationFailed(nextEpoch, error));
  }

  private SafeFuture<Void> createAndSendProposerPreferences(final ProposerDuties proposerDuties) {
    final List<ProposerDuty> ourDuties =
        proposerDuties.getDuties().stream()
            .filter(duty -> ownedValidators.hasValidator(duty.getPublicKey()))
            .toList();

    if (ourDuties.isEmpty()) {
      LOG.debug("No proposal duties for our validators in the next epoch");
      return SafeFuture.COMPLETE;
    }

    return forkProvider
        .getForkInfo(ourDuties.getFirst().getSlot())
        .thenCompose(
            forkInfo ->
                SafeFuture.collectAll(
                        ourDuties.stream()
                            .map(duty -> createSignedProposerPreferences(duty, forkInfo)))
                    .thenCompose(
                        signedPreferences -> {
                          final List<SignedProposerPreferences> preferencesList =
                              signedPreferences.stream().flatMap(Optional::stream).toList();
                          if (preferencesList.isEmpty()) {
                            return SafeFuture.COMPLETE;
                          }
                          LOG.debug("Publishing {} proposer preferences", preferencesList.size());
                          return validatorApiChannel.sendSignedProposerPreferences(preferencesList);
                        }));
  }

  private SafeFuture<Optional<SignedProposerPreferences>> createSignedProposerPreferences(
      final ProposerDuty duty, final ForkInfo forkInfo) {
    final Optional<Validator> maybeValidator = ownedValidators.getValidator(duty.getPublicKey());
    if (maybeValidator.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    final Optional<SafeFuture<Optional<SignedProposerPreferences>>> maybeFuture =
        proposerConfigPropertiesProvider
            .getFeeRecipient(duty.getPublicKey())
            .map(
                feeRecipient -> {
                  final UInt64 gasLimit =
                      proposerConfigPropertiesProvider.getGasLimit(duty.getPublicKey());
                  final SchemaDefinitionsGloas schemaDefinitions =
                      SchemaDefinitionsGloas.required(
                          spec.atSlot(duty.getSlot()).getSchemaDefinitions());

                  final ProposerPreferences proposerPreferences =
                      schemaDefinitions
                          .getProposerPreferencesSchema()
                          .create(
                              duty.getSlot(),
                              UInt64.valueOf(duty.getValidatorIndex()),
                              feeRecipient,
                              gasLimit);

                  return maybeValidator
                      .get()
                      .getSigner()
                      .signProposerPreferences(proposerPreferences, forkInfo)
                      .thenApply(
                          signature ->
                              Optional.of(
                                  schemaDefinitions
                                      .getSignedProposerPreferencesSchema()
                                      .create(proposerPreferences, signature)));
                });

    return maybeFuture.orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  private boolean isThirdSlotOfEpoch(final UInt64 slot) {
    return slot.mod(spec.getSlotsPerEpoch(slot)).equals(UInt64.valueOf(2));
  }

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {}

  @Override
  public void onPossibleMissedEvents() {}

  @Override
  public void onValidatorsAdded() {}

  @Override
  public void onBlockProductionDue(final UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {}

  @Override
  public void onSyncCommitteeCreationDue(final UInt64 slot) {}

  @Override
  public void onContributionCreationDue(final UInt64 slot) {}

  @Override
  public void onPayloadAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttesterSlashing(final AttesterSlashing attesterSlashing) {}

  @Override
  public void onProposerSlashing(final ProposerSlashing proposerSlashing) {}

  @Override
  public void onUpdatedValidatorStatuses(
      final Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses,
      final boolean possibleMissingEvents) {}
}

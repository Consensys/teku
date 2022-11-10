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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class BeaconProposerPreparer implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorApiChannel validatorApiChannel;
  private Optional<ValidatorIndexProvider> validatorIndexProvider;

  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider;
  private final Spec spec;

  private final AtomicBoolean firstCallDone = new AtomicBoolean(false);
  private final AtomicBoolean sentProposersAtLeastOnce = new AtomicBoolean(false);

  public BeaconProposerPreparer(
      final ValidatorApiChannel validatorApiChannel,
      final Optional<ValidatorIndexProvider> validatorIndexProvider,
      final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider,
      final Spec spec) {
    this.validatorApiChannel = validatorApiChannel;
    this.validatorIndexProvider = validatorIndexProvider;
    this.proposerConfigPropertiesProvider = proposerConfigPropertiesProvider;
    this.spec = spec;
  }

  public void initialize(final Optional<ValidatorIndexProvider> provider) {
    this.validatorIndexProvider = provider;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    if (validatorIndexProvider.isEmpty()) {
      return;
    }
    if (firstCallDone.compareAndSet(false, true) || isThirdSlotOfEpoch(slot)) {
      sendPreparableProposerList();
    }
  }

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {}

  @Override
  public void onPossibleMissedEvents() {
    sendPreparableProposerList();
  }

  @Override
  public void onValidatorsAdded() {
    sendPreparableProposerList();
  }

  @Override
  public void onBlockProductionDue(final UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {}

  private boolean isThirdSlotOfEpoch(final UInt64 slot) {
    return slot.mod(spec.getSlotsPerEpoch(slot)).equals(UInt64.valueOf(2));
  }

  private void sendPreparableProposerList() {
    if (validatorIndexProvider.isEmpty()) {
      return;
    }

    validatorIndexProvider
        .orElseThrow()
        .getValidatorIndicesByPublicKey()
        .thenCombine(
            proposerConfigPropertiesProvider.refresh(),
            (publicKeyToIndex, __) -> buildBeaconPreparableProposerList(publicKeyToIndex))
        .thenCompose(
            beaconPreparableProposers ->
                validatorApiChannel
                    .prepareBeaconProposer(beaconPreparableProposers)
                    .thenApply(__ -> beaconPreparableProposers))
        .finish(
            beaconPreparableProposers -> {
              LOG.debug(
                  "Information about {} proposers has been processed successfully by the Beacon Node.",
                  beaconPreparableProposers.size());
              sentProposersAtLeastOnce.compareAndSet(false, true);
            },
            VALIDATOR_LOGGER::beaconProposerPreparationFailed);
  }

  private Collection<BeaconPreparableProposer> buildBeaconPreparableProposerList(
      final Map<BLSPublicKey, Integer> blsPublicKeyToIndexMap) {
    return blsPublicKeyToIndexMap.entrySet().stream()
        .map(
            entry ->
                proposerConfigPropertiesProvider
                    .getFeeRecipient(entry.getKey())
                    .map(
                        eth1Address ->
                            new BeaconPreparableProposer(
                                UInt64.valueOf(entry.getValue()), eth1Address)))
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
  }
}

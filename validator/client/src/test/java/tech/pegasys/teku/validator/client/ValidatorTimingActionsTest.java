/*
 * Copyright Consensys Software Inc., 2024
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashingRiskAction;
import tech.pegasys.teku.validator.client.validatorslashingprotection.SlashedValidatorAlert;

public class ValidatorTimingActionsTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private final ValidatorTimingChannel delegate = mock(ValidatorTimingChannel.class);
  private final List<ValidatorTimingChannel> delegates = List.of(delegate);
  private final MetricsSystem metricsSystem = mock(MetricsSystem.class);
  private final UInt64 firstSlashedIndex = UInt64.valueOf(254);
  private final BLSPublicKey firstSlashedPublicKey = dataStructureUtil.randomPublicKey();
  private final UInt64 secondSlashedIndex = UInt64.valueOf(54654);
  private final BLSPublicKey secondSlashedPublicKey = dataStructureUtil.randomPublicKey();
  private final StatusLogger statusLogger = mock(StatusLogger.class);
  private final Optional<SlashingRiskAction> maybeSlashedValidatorAction =
      Optional.of(new SlashedValidatorAlert(statusLogger));

  @Test
  public void shouldPrintAlertForSlashedValidators_AttesterSlashing() {
    final ValidatorTimingActions validatorTimingActions =
        new ValidatorTimingActions(
            validatorIndexProvider, delegates, spec, metricsSystem, maybeSlashedValidatorAction);
    final AttesterSlashing attesterSlashing =
        dataStructureUtil.randomAttesterSlashing(
            dataStructureUtil.randomValidatorIndex(),
            firstSlashedIndex,
            secondSlashedIndex,
            dataStructureUtil.randomValidatorIndex());
    when(validatorIndexProvider.getPublicKey(any())).thenReturn(Optional.empty());
    when(validatorIndexProvider.getPublicKey(firstSlashedIndex.intValue()))
        .thenReturn(Optional.of(firstSlashedPublicKey));
    when(validatorIndexProvider.getPublicKey(secondSlashedIndex.intValue()))
        .thenReturn(Optional.of(secondSlashedPublicKey));
    validatorTimingActions.onAttesterSlashing(attesterSlashing);
    verify(delegate).onAttesterSlashing(attesterSlashing);
    verify(validatorIndexProvider).getPublicKey(firstSlashedIndex.intValue());
    verify(validatorIndexProvider).getPublicKey(secondSlashedIndex.intValue());
    verify(statusLogger)
        .validatorSlashedAlert(
            Set.of(firstSlashedPublicKey.toHexString(), secondSlashedPublicKey.toHexString()));
  }

  @Test
  public void shouldPrintAlertForSlashedValidators_ProposerSlashing() {
    final ValidatorTimingActions validatorTimingActions =
        new ValidatorTimingActions(
            validatorIndexProvider, delegates, spec, metricsSystem, maybeSlashedValidatorAction);
    final ProposerSlashing proposerSlashing =
        dataStructureUtil.randomProposerSlashing(dataStructureUtil.randomSlot(), firstSlashedIndex);
    when(validatorIndexProvider.getPublicKey(any())).thenReturn(Optional.empty());
    when(validatorIndexProvider.getPublicKey(firstSlashedIndex.intValue()))
        .thenReturn(Optional.of(firstSlashedPublicKey));
    validatorTimingActions.onProposerSlashing(proposerSlashing);
    verify(delegate).onProposerSlashing(proposerSlashing);
    verify(validatorIndexProvider).getPublicKey(firstSlashedIndex.intValue());
    verify(statusLogger).validatorSlashedAlert(Set.of(firstSlashedPublicKey.toHexString()));
  }

  @Test
  public void shouldNotTriggerSlashingActionForNotOwnedValidator_AttesterSlashing() {
    final ValidatorTimingActions validatorTimingActions =
        new ValidatorTimingActions(
            validatorIndexProvider, delegates, spec, metricsSystem, maybeSlashedValidatorAction);
    final AttesterSlashing attesterSlashing =
        dataStructureUtil.randomAttesterSlashing(firstSlashedIndex, secondSlashedIndex);
    when(validatorIndexProvider.getPublicKey(any())).thenReturn(Optional.empty());
    validatorTimingActions.onAttesterSlashing(attesterSlashing);
    verify(delegate).onAttesterSlashing(attesterSlashing);
    verify(validatorIndexProvider).getPublicKey(firstSlashedIndex.intValue());
    verify(validatorIndexProvider).getPublicKey(secondSlashedIndex.intValue());
    verify(statusLogger, never()).validatorSlashedAlert(any());
  }

  @Test
  public void shouldNotTriggerSlashingActionForNotOwnedValidator_ProposerSlashing() {
    final ValidatorTimingActions validatorTimingActions =
        new ValidatorTimingActions(
            validatorIndexProvider, delegates, spec, metricsSystem, maybeSlashedValidatorAction);
    final ProposerSlashing proposerSlashing =
        dataStructureUtil.randomProposerSlashing(dataStructureUtil.randomSlot(), firstSlashedIndex);
    when(validatorIndexProvider.getPublicKey(any())).thenReturn(Optional.empty());
    validatorTimingActions.onProposerSlashing(proposerSlashing);
    verify(delegate).onProposerSlashing(proposerSlashing);
    verify(validatorIndexProvider).getPublicKey(firstSlashedIndex.intValue());
    verify(statusLogger, never()).validatorSlashedAlert(any());
  }

  @Test
  public void shouldNotTriggerValidatorSlashingActionWhenNotEnabled_AttesterSlashing() {
    final ValidatorTimingActions validatorTimingActions =
        new ValidatorTimingActions(
            validatorIndexProvider, delegates, spec, metricsSystem, Optional.empty());
    final AttesterSlashing attesterSlashing =
        dataStructureUtil.randomAttesterSlashing(
            dataStructureUtil.randomValidatorIndex(), dataStructureUtil.randomValidatorIndex());
    validatorTimingActions.onAttesterSlashing(attesterSlashing);
    verify(delegate).onAttesterSlashing(attesterSlashing);
    verifyNoInteractions(validatorIndexProvider);
    verifyNoInteractions(statusLogger);
  }

  @Test
  public void shouldNotTriggerValidatorSlashingActionWhenNotEnabled_ProposerSlashing() {
    final ValidatorTimingActions validatorTimingActions =
        new ValidatorTimingActions(
            validatorIndexProvider, delegates, spec, metricsSystem, Optional.empty());
    final ProposerSlashing proposerSlashing =
        dataStructureUtil.randomProposerSlashing(
            dataStructureUtil.randomSlot(), dataStructureUtil.randomValidatorIndex());
    validatorTimingActions.onProposerSlashing(proposerSlashing);
    verify(delegate).onProposerSlashing(proposerSlashing);
    verifyNoInteractions(validatorIndexProvider);
    verifyNoInteractions(statusLogger);
  }
}

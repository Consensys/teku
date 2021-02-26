/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.spec.util.operationvalidators.AttesterSlashingStateTransitionValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttesterSlashingValidatorTest {
  private DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private RecentChainData recentChainData = mock(RecentChainData.class);
  private final SpecProvider specProvider = mock(SpecProvider.class);
  private AttesterSlashingValidator attesterSlashingValidator;

  @BeforeEach
  void beforeEach() {
    when(recentChainData.getBestState())
        .thenReturn(Optional.of(dataStructureUtil.randomBeaconState()));
    attesterSlashingValidator = new AttesterSlashingValidator(specProvider, recentChainData);
  }

  @Test
  public void shouldAcceptValidAttesterSlashing() {
    AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(specProvider.validateAttesterSlashing(
            recentChainData.getBestState().orElseThrow(), slashing))
        .thenReturn(Optional.empty());
    assertTrue(attesterSlashingValidator.validateFully(slashing).isAccept());
  }

  @Test
  public void shouldRejectInvalidAttesterSlashing() {
    AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(specProvider.validateAttesterSlashing(
            recentChainData.getBestState().orElseThrow(), slashing))
        .thenReturn(
            Optional.of(
                AttesterSlashingStateTransitionValidator.AttesterSlashingInvalidReason
                    .ATTESTATIONS_NOT_SLASHABLE));
    assertTrue(attesterSlashingValidator.validateFully(slashing).isReject());
  }

  @Test
  public void shouldIgnoreAttesterSlashingForTheSameAttesters() {
    AttesterSlashing slashing1 = dataStructureUtil.randomAttesterSlashing();
    AttesterSlashing slashing2 =
        new AttesterSlashing(slashing1.getAttestation_1(), slashing1.getAttestation_2());
    when(specProvider.validateAttesterSlashing(
            eq(recentChainData.getBestState().orElseThrow()), any()))
        .thenReturn(Optional.empty());
    assertTrue(attesterSlashingValidator.validateFully(slashing1).isAccept());
    assertTrue(attesterSlashingValidator.validateFully(slashing2).isIgnore());
  }
}

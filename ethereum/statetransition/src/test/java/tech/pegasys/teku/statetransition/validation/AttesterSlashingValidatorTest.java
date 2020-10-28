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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.REJECT;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.operationvalidators.AttesterSlashingStateTransitionValidator;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttesterSlashingValidatorTest {
  private DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private RecentChainData recentChainData = mock(RecentChainData.class);
  private AttesterSlashingValidator attesterSlashingValidator;
  private AttesterSlashingStateTransitionValidator stateTransitionValidator;

  @BeforeEach
  void beforeEach() {
    when(recentChainData.getBestState())
        .thenReturn(Optional.of(dataStructureUtil.randomBeaconState()));
    stateTransitionValidator = mock(AttesterSlashingStateTransitionValidator.class);
    attesterSlashingValidator =
        new AttesterSlashingValidator(recentChainData, stateTransitionValidator);
  }

  @Test
  public void shouldAcceptValidAttesterSlashing() {
    AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(stateTransitionValidator.validate(recentChainData.getBestState().orElseThrow(), slashing))
        .thenReturn(Optional.empty());
    assertThat(attesterSlashingValidator.validateFully(slashing)).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectInvalidAttesterSlashing() {
    AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(stateTransitionValidator.validate(recentChainData.getBestState().orElseThrow(), slashing))
        .thenReturn(
            Optional.of(
                AttesterSlashingStateTransitionValidator.AttesterSlashingInvalidReason
                    .ATTESTATIONS_NOT_SLASHABLE));
    assertThat(attesterSlashingValidator.validateFully(slashing)).isEqualTo(REJECT);
  }

  @Test
  public void shouldIgnoreAttesterSlashingForTheSameAttesters() {
    AttesterSlashing slashing1 = dataStructureUtil.randomAttesterSlashing();
    AttesterSlashing slashing2 =
        new AttesterSlashing(slashing1.getAttestation_1(), slashing1.getAttestation_2());
    when(stateTransitionValidator.validate(eq(recentChainData.getBestState().orElseThrow()), any()))
        .thenReturn(Optional.empty());
    assertThat(attesterSlashingValidator.validateFully(slashing1)).isEqualTo(ACCEPT);
    assertThat(attesterSlashingValidator.validateFully(slashing2)).isEqualTo(IGNORE);
  }
}

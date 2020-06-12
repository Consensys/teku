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

package tech.pegasys.teku.statetransition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.blocks.BeaconBlockBodyLists.createAttesterSlashings;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.operationvalidators.OperationStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.ProposerSlashingStateTransitionValidator;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.util.config.Constants;

@SuppressWarnings("unchecked")
public class OperationPoolTest {

  DataStructureUtil dataStructureUtil = new DataStructureUtil();
  BeaconState state = mock(BeaconState.class);

  @Test
  void emptyPoolShouldReturnEmptyList() {
    OperationStateTransitionValidator<ProposerSlashing> validator =
        mock(OperationStateTransitionValidator.class);
    OperationPool<ProposerSlashing> pool = new OperationPool<>(ProposerSlashing.class, validator);
    when(validator.validate(any(), any())).thenReturn(Optional.empty());
    assertThat(pool.getItemsForBlock(state)).isEmpty();
  }

  @Test
  void shouldAddMaxItemsToPool() {
    OperationStateTransitionValidator<SignedVoluntaryExit> validator =
        mock(OperationStateTransitionValidator.class);
    OperationPool<SignedVoluntaryExit> pool =
        new OperationPool<>(SignedVoluntaryExit.class, validator);
    when(validator.validate(any(), any())).thenReturn(Optional.empty());
    for (int i = 0; i < Constants.MAX_VOLUNTARY_EXITS + 1; i++) {
      pool.add(dataStructureUtil.randomSignedVoluntaryExit());
    }
    assertThat(pool.getItemsForBlock(state)).hasSize(Constants.MAX_VOLUNTARY_EXITS);
  }

  @Test
  void shouldRemoveAllItemsFromPool() {
    OperationStateTransitionValidator<AttesterSlashing> validator =
        mock(OperationStateTransitionValidator.class);
    OperationPool<AttesterSlashing> pool = new OperationPool<>(AttesterSlashing.class, validator);
    SSZMutableList<AttesterSlashing> slashingsInBlock = createAttesterSlashings();
    when(validator.validate(any(), any())).thenReturn(Optional.empty());
    for (int i = 0; i < Constants.MAX_ATTESTER_SLASHINGS; i++) {
      AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
      pool.add(slashing);
      slashingsInBlock.add(slashing);
    }
    pool.removeAll(slashingsInBlock);
    assertThat(pool.getItemsForBlock(state)).isEmpty();
  }

  @Test
  void shouldNotIncludeInvalidatedItemsFromPool() {
    OperationStateTransitionValidator<ProposerSlashing> validator =
        mock(OperationStateTransitionValidator.class);
    OperationPool<ProposerSlashing> pool = new OperationPool<>(ProposerSlashing.class, validator);

    ProposerSlashing slashing1 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing2 = dataStructureUtil.randomProposerSlashing();

    pool.add(slashing1);
    pool.add(slashing2);

    when(validator.validate(any(), eq(slashing1)))
        .thenReturn(
            Optional.of(
                ProposerSlashingStateTransitionValidator.ProposerSlashingInvalidReason
                    .HEADER_SLOTS_DIFFERENT));
    when(validator.validate(any(), eq(slashing2))).thenReturn(Optional.empty());
    assertThat(pool.getItemsForBlock(state)).containsOnly(slashing2);
  }
}

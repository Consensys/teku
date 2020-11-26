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

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.util.config.Constants;

@SuppressWarnings({"unchecked", "FutureReturnValueIgnored"})
public class OperationPoolTest {

  DataStructureUtil dataStructureUtil = new DataStructureUtil();
  BeaconState state = mock(BeaconState.class);

  @Test
  void emptyPoolShouldReturnEmptyList() {
    OperationValidator<ProposerSlashing> validator = mock(OperationValidator.class);
    OperationPool<ProposerSlashing> pool = new OperationPool<>(ProposerSlashing.class, validator);
    assertThat(pool.getItemsForBlock(state)).isEmpty();
  }

  @Test
  void shouldAddMaxItemsToPool() {
    OperationValidator<SignedVoluntaryExit> validator = mock(OperationValidator.class);
    OperationPool<SignedVoluntaryExit> pool =
        new OperationPool<>(SignedVoluntaryExit.class, validator);
    when(validator.validateFully(any())).thenReturn(InternalValidationResult.ACCEPT);
    when(validator.validateForStateTransition(any(), any())).thenReturn(true);
    for (int i = 0; i < Constants.MAX_VOLUNTARY_EXITS + 1; i++) {
      pool.add(dataStructureUtil.randomSignedVoluntaryExit());
    }
    assertThat(pool.getItemsForBlock(state)).hasSize(Constants.MAX_VOLUNTARY_EXITS);
  }

  @Test
  void shouldRemoveAllItemsFromPool() {
    OperationValidator<AttesterSlashing> validator = mock(OperationValidator.class);
    OperationPool<AttesterSlashing> pool = new OperationPool<>(AttesterSlashing.class, validator);
    SSZMutableList<AttesterSlashing> slashingsInBlock = createAttesterSlashings();
    when(validator.validateFully(any())).thenReturn(InternalValidationResult.ACCEPT);
    when(validator.validateForStateTransition(any(), any())).thenReturn(true);
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
    OperationValidator<ProposerSlashing> validator = mock(OperationValidator.class);
    OperationPool<ProposerSlashing> pool = new OperationPool<>(ProposerSlashing.class, validator);

    ProposerSlashing slashing1 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing2 = dataStructureUtil.randomProposerSlashing();

    when(validator.validateFully(any())).thenReturn(InternalValidationResult.ACCEPT);

    pool.add(slashing1);
    pool.add(slashing2);

    when(validator.validateForStateTransition(any(), eq(slashing1))).thenReturn(false);
    when(validator.validateForStateTransition(any(), eq(slashing2))).thenReturn(true);

    assertThat(pool.getItemsForBlock(state)).containsOnly(slashing2);
  }

  @Test
  void subscribeOperationAdded() {
    OperationValidator<ProposerSlashing> validator = mock(OperationValidator.class);
    OperationPool<ProposerSlashing> pool = new OperationPool<>(ProposerSlashing.class, validator);

    // Set up subscriber
    final Map<ProposerSlashing, InternalValidationResult> addedSlashings = new HashMap<>();
    OperationPool.OperationAddedSubscriber<ProposerSlashing> subscriber = addedSlashings::put;
    pool.subscribeOperationAdded(subscriber);

    ProposerSlashing slashing1 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing2 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing3 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing4 = dataStructureUtil.randomProposerSlashing();

    when(validator.validateFully(slashing1)).thenReturn(InternalValidationResult.ACCEPT);
    when(validator.validateFully(slashing2)).thenReturn(InternalValidationResult.SAVE_FOR_FUTURE);
    when(validator.validateFully(slashing3)).thenReturn(InternalValidationResult.REJECT);
    when(validator.validateFully(slashing4)).thenReturn(InternalValidationResult.IGNORE);

    pool.add(slashing1);
    pool.add(slashing2);
    pool.add(slashing3);
    pool.add(slashing4);

    assertThat(addedSlashings.size()).isEqualTo(2);
    assertThat(addedSlashings).containsKey(slashing1);
    assertThat(addedSlashings.get(slashing1)).isEqualTo(InternalValidationResult.ACCEPT);
    assertThat(addedSlashings).containsKey(slashing2);
    assertThat(addedSlashings.get(slashing2)).isEqualTo(InternalValidationResult.SAVE_FOR_FUTURE);
  }
}

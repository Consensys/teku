/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;

abstract class AbstractEpochProcessorTestBase {
  private final Spec spec;
  private final DataStructureUtil dataStructureUtil;
  private final EpochProcessor processor;

  public AbstractEpochProcessorTestBase(final Spec spec) {
    this.spec = spec;
    this.dataStructureUtil = new DataStructureUtil(spec);
    this.processor = spec.atSlot(UInt64.ZERO).getEpochProcessor();
  }

  /**
   * Updating the effective balance to the same value is wasteful and results in new validator
   * objects being created for every validator, every epoch even though they usually are unchanged.
   */
  @Test
  void shouldNotUpdateEffectiveBalanceWhenAlreadyAtMaxValue() {
    final UInt64 maxEffectiveBalance = spec.getGenesisSpecConfig().getMaxEffectiveBalance();
    final Validator validator =
        spy(dataStructureUtil.randomValidator().withEffective_balance(maxEffectiveBalance));
    when(validator.getEffective_balance()).thenReturn(maxEffectiveBalance);
    final BeaconStatePhase0 state =
        dataStructureUtil
            .stateBuilderPhase0(1, 1)
            .balances(maxEffectiveBalance.times(2))
            .validators(validator)
            .build();
    state.updated(processor::processEffectiveBalanceUpdates);
    verify(validator, never()).withEffective_balance(any());
  }
}

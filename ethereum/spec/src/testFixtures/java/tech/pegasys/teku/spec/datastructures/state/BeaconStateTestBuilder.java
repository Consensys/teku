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

package tech.pegasys.teku.spec.datastructures.state;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconStateTestBuilder {
  private final List<Validator> validators = new ArrayList<>();
  private final List<UInt64> balances = new ArrayList<>();
  private UInt64 slot;
  private Fork fork;
  private final DataStructureUtil dataStructureUtil;

  public BeaconStateTestBuilder(final DataStructureUtil dataStructureUtil) {
    this.slot = dataStructureUtil.randomUInt64();
    this.fork = dataStructureUtil.randomFork();
    this.dataStructureUtil = dataStructureUtil;
  }

  public BeaconStateTestBuilder slot(final long slot) {
    this.slot = UInt64.valueOf(slot);
    return this;
  }

  public BeaconStateTestBuilder forkVersion(final Bytes4 onlyForkVersion) {
    fork = new Fork(onlyForkVersion, onlyForkVersion, GENESIS_EPOCH);
    return this;
  }

  public BeaconStateTestBuilder validator(final Validator validator) {
    validators.add(validator);
    balances.add(validator.getEffective_balance());
    return this;
  }

  public BeaconStateTestBuilder activeValidator(final UInt64 effectiveBalance) {
    validators.add(
        dataStructureUtil
            .randomValidator()
            .withEffective_balance(effectiveBalance)
            .withActivation_epoch(UInt64.ZERO)
            .withExit_epoch(FAR_FUTURE_EPOCH));
    return this;
  }

  public BeaconState build() {
    final SpecVersion specVersion = dataStructureUtil.getSpec().atSlot(slot);
    return specVersion
        .getSchemaDefinitions()
        .getBeaconStateSchema()
        .createEmpty()
        .updated(
            state -> {
              state.setSlot(slot);
              state.setFork(fork);
              state.getValidators().appendAll(validators);
              state.getBalances().appendAllElements(balances);
            });
  }
}

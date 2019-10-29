/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.consensus.transition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.ChainStart;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.Time;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.collections.ReadList;
import tech.pegasys.artemis.util.uint.UInt64;

public class InitialStateTransitionTest {

  @Test
  public void handleChainStartCorrectly() {
    BeaconChainSpec spec = BeaconChainSpec.createWithDefaults();
    Random rnd = new Random();
    Time eth1Time = Time.castFrom(UInt64.random(rnd));
    List<Deposit> deposits = Collections.emptyList();
    ReadList<Integer, DepositData> depositDataList =
        ReadList.wrap(
            deposits.stream().map(Deposit::getData).collect(Collectors.toList()),
            Integer::new,
            1L << spec.getConstants().getDepositContractTreeDepth().getIntValue());
    Eth1Data eth1Data =
        new Eth1Data(spec.hash_tree_root(depositDataList), UInt64.ZERO, Hash32.random(rnd));
    InitialStateTransition initialStateTransition =
        new InitialStateTransition(
            new ChainStart(eth1Time, eth1Data, Collections.emptyList()), spec);

    BeaconState initialState = initialStateTransition.apply(spec.get_empty_block());

    Time expectedTime =
        Time.castFrom(eth1Time.minus(eth1Time.modulo(spec.getConstants().getSecondsPerDay())))
            .plus(spec.getConstants().getSecondsPerDay().times(2));

    assertThat(initialState.getGenesisTime()).isEqualTo(expectedTime);
    assertThat(initialState.getEth1Data()).isEqualTo(eth1Data);
  }
}

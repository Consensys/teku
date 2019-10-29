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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Random;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.ChainStart;
import org.ethereum.beacon.consensus.TestUtils;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.Time;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.uint.UInt64;

public class PerSlotTransitionTest {

  @Test
  public void test1() {
    Random rnd = new Random();
    Time genesisTime = Time.castFrom(UInt64.random(rnd));
    SpecConstants specConstants =
        new SpecConstants() {
          @Override
          public SlotNumber.EpochLength getSlotsPerEpoch() {
            return new SlotNumber.EpochLength(UInt64.valueOf(8));
          }
        };
    BeaconChainSpec spec =
        new BeaconChainSpec.Builder()
            .withConstants(specConstants)
            .withDefaultHasher(specConstants)
            .withDefaultHashFunction()
            .withVerifyDepositProof(false)
            .build();

    List<Deposit> deposits = TestUtils.getAnyDeposits(rnd, spec, 8).getValue0();
    Eth1Data eth1Data =
        new Eth1Data(Hash32.random(rnd), UInt64.valueOf(deposits.size()), Hash32.random(rnd));
    InitialStateTransition initialStateTransition =
        new InitialStateTransition(new ChainStart(genesisTime, eth1Data, deposits), spec);

    BeaconStateEx initialState = initialStateTransition.apply(spec.get_empty_block());
    BeaconStateEx s1State = ExtendedSlotTransition.create(spec).apply(initialState);
    BeaconStateEx s2State = ExtendedSlotTransition.create(spec).apply(s1State);
    BeaconStateEx s3State = ExtendedSlotTransition.create(spec).apply(s2State);

    assertEquals(specConstants.getGenesisSlot().plus(3), s3State.getSlot());
  }
}

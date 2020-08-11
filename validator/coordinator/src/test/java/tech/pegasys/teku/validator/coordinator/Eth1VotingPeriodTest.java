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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

class Eth1VotingPeriodTest {

  private static final UInt64 GENESIS_TIME = UInt64.valueOf(1000);
  private static final UInt64 START_SLOT = UInt64.valueOf(100);
  private static final UInt64 NEXT_VOTING_PERIOD_SLOT = UInt64.valueOf(102);

  private final Eth1VotingPeriod votingPeriod = new Eth1VotingPeriod();

  // Voting Period Start Time
  // = genesisTime + ((slot - (slot % slots_per_eth1_voting_period)) * seconds_per_slot)
  // = 1000 + (((100 - (100 % 6)) * 4)
  // = 1384
  //
  // Spec Range:
  //    Lower Bound = 1384 - (5 * 3 * 2) = 1354
  //    Upper Bound = 1384 - (5 * 3) = 1369

  // Next Voting Period Start Slot = 102
  // Next Voting Period Start Time = 1408
  // Next Voting Period Lower Bound = 1378

  @BeforeAll
  static void setConstants() {
    Constants.SECONDS_PER_ETH1_BLOCK = UInt64.valueOf(3);
    Constants.ETH1_FOLLOW_DISTANCE = UInt64.valueOf(5);
    Constants.EPOCHS_PER_ETH1_VOTING_PERIOD = 1;
    Constants.SLOTS_PER_EPOCH = 6;
    Constants.SECONDS_PER_SLOT = 4;
  }

  @AfterAll
  static void restoreConstants() {
    Constants.setConstants("minimal");
  }

  @Test
  void checkTimeValues() {
    assertThat(votingPeriod.getSpecRangeLowerBound(START_SLOT, GENESIS_TIME))
        .isEqualByComparingTo(UInt64.valueOf(1354));
    assertThat(votingPeriod.getSpecRangeUpperBound(START_SLOT, GENESIS_TIME))
        .isEqualByComparingTo(UInt64.valueOf(1369));
    assertThat(votingPeriod.getSpecRangeLowerBound(NEXT_VOTING_PERIOD_SLOT, GENESIS_TIME))
        .isEqualByComparingTo(UInt64.valueOf(1378));
  }

  @Test
  void checkTimeValuesStayAboveZero() {
    assertThat(votingPeriod.getSpecRangeLowerBound(ONE, ZERO)).isEqualByComparingTo(UInt64.ZERO);
    assertThat(votingPeriod.getSpecRangeUpperBound(ONE, ZERO)).isEqualByComparingTo(UInt64.ZERO);
  }

  @Test
  void shouldCalculateCacheDuration() {
    // SECONDS_PER_SLOT + (EPOCHS_PER_ETH1_VOTING_PERIOD * SLOTS_PER_EPOCH * SECONDS_PER_SLOT) +
    // (SECONDS_PER_ETH1_BLOCK * ETH1_FOLLOW_DISTANCE) +
    // (SLOTS_PER_EPOCH * SECONDS_PER_SLOT)
    // So 4 + (1 * 6 * 4) + (3 * 5) + (6 * 4) = 67
    assertThat(votingPeriod.getCacheDurationInSeconds()).isEqualTo(UInt64.valueOf(67));
  }
}

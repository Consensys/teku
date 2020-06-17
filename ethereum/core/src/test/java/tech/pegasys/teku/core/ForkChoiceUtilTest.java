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

package tech.pegasys.teku.core;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;

public class ForkChoiceUtilTest {
  private final UnsignedLong genesisTime = UnsignedLong.valueOf("1591924193");
  final UnsignedLong slot50 =
      genesisTime.plus(UnsignedLong.valueOf(SECONDS_PER_SLOT).times(UnsignedLong.valueOf(50L)));

  @Test
  public void getCurrentSlot_shouldGetZeroAtGenesis() {
    assertThat(ForkChoiceUtil.getCurrentSlot(genesisTime, genesisTime))
        .isEqualTo(UnsignedLong.ZERO);
  }

  @Test
  public void getCurrentSlot_shouldGetNonZeroPastGenesis() {

    assertThat(ForkChoiceUtil.getCurrentSlot(slot50, genesisTime))
        .isEqualTo(UnsignedLong.valueOf(50L));
  }

  @Test
  public void getSlotStartTime_shouldGetGenesisTimeForBlockZero() {
    assertThat(ForkChoiceUtil.getSlotStartTime(UnsignedLong.ZERO, genesisTime))
        .isEqualTo(genesisTime);
  }

  @Test
  public void getSlotStartTime_shouldGetCorrectTimePastGenesis() {
    assertThat(ForkChoiceUtil.getSlotStartTime(UnsignedLong.valueOf(50L), genesisTime))
        .isEqualTo(slot50);
  }
}

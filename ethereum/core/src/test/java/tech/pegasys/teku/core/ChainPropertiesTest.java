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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;

public class ChainPropertiesTest {

  @Test
  public void computeBestEpochFinalizableAtSlot_atEpochBoundary() {
    final UnsignedLong epoch = UnsignedLong.valueOf(2);
    final UnsignedLong startSlot = compute_start_slot_at_epoch(epoch);

    final UnsignedLong result = ChainProperties.computeBestEpochFinalizableAtSlot(startSlot);
    assertThat(result).isEqualTo(epoch);
  }

  @Test
  public void computeBestEpochFinalizableAtSlot_priorToEpochBoundary() {
    final UnsignedLong epoch = UnsignedLong.valueOf(2);
    final UnsignedLong slot = compute_start_slot_at_epoch(epoch).minus(UnsignedLong.ONE);

    final UnsignedLong result = ChainProperties.computeBestEpochFinalizableAtSlot(slot);
    assertThat(result).isEqualTo(epoch);
  }

  @Test
  public void computeBestEpochFinalizableAtSlot_afterEpochBoundary() {
    final UnsignedLong epoch = UnsignedLong.valueOf(2);
    final UnsignedLong slot = compute_start_slot_at_epoch(epoch).plus(UnsignedLong.ONE);

    final UnsignedLong result = ChainProperties.computeBestEpochFinalizableAtSlot(slot);
    assertThat(result).isEqualTo(epoch.plus(UnsignedLong.ONE));
  }
}

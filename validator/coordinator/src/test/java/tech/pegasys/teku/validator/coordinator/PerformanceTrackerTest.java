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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

public class PerformanceTrackerTest {

  private RecentChainData recentChainData = mock(RecentChainData.class);
  private PerformanceTracker performanceTracker = new PerformanceTracker(recentChainData);

  private StatusLogger log = Mockito.mock(StatusLogger.class);

  @BeforeAll
  void setUp() {
    Constants.SLOTS_PER_EPOCH = 4;
  }

  @Test
  void test() {
    Bytes32 BLOCK_ROOT_1 = Bytes32.fromHexString("0x1");
    Bytes32 BLOCK_ROOT_2 = Bytes32.fromHexString("0x2");
    Bytes32 BLOCK_ROOT_3 = Bytes32.fromHexString("0x3");
    Bytes32 BLOCK_ROOT_4 = Bytes32.fromHexString("0x3");
    when(recentChainData.getBlockRootBySlot(any())).thenReturn(Optional.empty());
    when(recentChainData.getBlockRootBySlot(UInt64.valueOf(1)))
        .thenReturn(Optional.of(BLOCK_ROOT_1));
    when(recentChainData.getBlockRootBySlot(UInt64.valueOf(2)))
        .thenReturn(Optional.of(BLOCK_ROOT_2));
    when(recentChainData.getBlockRootBySlot(UInt64.valueOf(3)))
        .thenReturn(Optional.of(BLOCK_ROOT_3));
    when(recentChainData.getBlockRootBySlot(UInt64.valueOf(4)))
        .thenReturn(Optional.of(BLOCK_ROOT_4));
  }
}

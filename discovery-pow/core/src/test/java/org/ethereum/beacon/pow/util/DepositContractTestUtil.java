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

package org.ethereum.beacon.pow.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.util.Eth1DataTestUtil;
import org.ethereum.beacon.pow.DepositContract;
import org.ethereum.beacon.pow.DepositContract.DepositInfo;
import org.mockito.Mockito;

public abstract class DepositContractTestUtil {
  private DepositContractTestUtil() {}

  public static DepositContract mockDepositContract(Random random, List<DepositInfo> deposits) {
    return mockDepositContract(deposits, Eth1DataTestUtil.createRandom(random));
  }

  public static DepositContract mockDepositContract(List<DepositInfo> deposits, Eth1Data eth1Data) {
    DepositContract depositContract = Mockito.mock(DepositContract.class);
    Mockito.when(depositContract.getLatestEth1Data()).thenReturn(Optional.of(eth1Data));
    Mockito.when(depositContract.peekDeposits(anyInt(), any(), any())).thenReturn(deposits);
    Mockito.when(depositContract.hasDepositRoot(any(), any())).thenReturn(true);
    return depositContract;
  }
}

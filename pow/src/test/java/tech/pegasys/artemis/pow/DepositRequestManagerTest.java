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

package tech.pegasys.artemis.pow;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import tech.pegasys.artemis.pow.api.DepositEventChannel;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.StubAsyncRunner;

public class DepositRequestManagerTest {

  private Eth1Provider eth1Provider;
  private Web3j web3j;
  private DepositEventChannel depositEventChannel;
  private DepositContract depositContract;
  private AsyncRunner asyncRunner;

  private DepositRequestManager depositRequestManager;

  @BeforeEach
  void setUp() {
    web3j = mock(Web3j.class);
    eth1Provider = new Eth1Provider(web3j);
    depositEventChannel = mock(DepositEventChannel.class);
    depositContract = mock(DepositContract.class);
    asyncRunner = new StubAsyncRunner();

    depositRequestManager =
        new DepositRequestManager(eth1Provider, asyncRunner, depositEventChannel, depositContract);
  }

  @Test
  void shouldProcessDepositsInOrder() {
    depositRequestManager.start();
    depositRequestManager.stop();
  }
}

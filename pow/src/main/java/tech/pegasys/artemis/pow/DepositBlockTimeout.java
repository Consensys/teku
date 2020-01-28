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

import com.google.common.eventbus.Subscribe;
import java.util.Date;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.artemis.pow.event.Deposit;

final class DepositBlockTimeout {
  private static final int EVENT_TIMEOUT = 5000;
  private final BlockBatcher batcher;
  private long lastPublishTime = 0;

  DepositBlockTimeout(final BlockBatcher batcher) {
    this.batcher = batcher;
  }

  public synchronized void onDepositEvent(final Block block, final Deposit deposit) {
    batcher.onDepositEvent(block, deposit);
    lastPublishTime = System.currentTimeMillis();
  }

  @Subscribe
  public synchronized void onTick(final Date date) {
    if (lastPublishTime != 0 && date.getTime() - lastPublishTime > EVENT_TIMEOUT) {
      batcher.forcePublishPendingBlock();
      lastPublishTime = 0;
    }
  }
}

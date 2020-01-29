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
import tech.pegasys.artemis.util.time.TimeProvider;

final class PublishOnInactivityDepositHandler {
  static final int EVENT_TIMEOUT_MILLIS = 5000;
  private final TimeProvider timeProvider;
  private final BatchByBlockDepositHandler batcher;
  private long lastPublishTime = 0;

  PublishOnInactivityDepositHandler(
      final TimeProvider timeProvider, final BatchByBlockDepositHandler batcher) {
    this.timeProvider = timeProvider;
    this.batcher = batcher;
  }

  public synchronized void onDepositEvent(final Block block, final Deposit deposit) {
    batcher.onDepositEvent(block, deposit);
    lastPublishTime = timeProvider.getTimeInMillis().longValue();
  }

  @Subscribe
  public synchronized void onTick(final Date date) {
    if (lastPublishTime != 0 && date.getTime() - lastPublishTime >= EVENT_TIMEOUT_MILLIS) {
      batcher.publishPendingBlock();
      lastPublishTime = 0;
    }
  }
}

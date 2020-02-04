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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Date;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.util.time.StubTimeProvider;

class PublishOnInactivityDepositHandlerTest {
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(10000);
  private final BatchByBlockDepositHandler batcher = mock(BatchByBlockDepositHandler.class);

  private final Block block1 = mock(Block.class);
  private final Block block2 = mock(Block.class);
  private final Deposit deposit1 = mock(Deposit.class);
  private final Deposit deposit2 = mock(Deposit.class);

  private final PublishOnInactivityDepositHandler handler =
      new PublishOnInactivityDepositHandler(timeProvider, batcher);

  @Test
  public void shouldPassDepositEventsThrough() {
    handler.onDepositEvent(block1, deposit1);

    verify(batcher).onDepositEvent(block1, deposit1);
    verifyNoMoreInteractions(batcher);
  }

  @Test
  public void shouldPublishPendingBlockAfterPeriodOfInactivity() {
    handler.onDepositEvent(block1, deposit1);

    timeProvider.advanceTimeByMillis(PublishOnInactivityDepositHandler.EVENT_TIMEOUT_MILLIS);

    tick();

    verify(batcher).publishPendingBlock();
  }

  @Test
  public void shouldNotPublishPendingBlockIfFurtherActivityHappensBeforeTimeout() {
    handler.onDepositEvent(block1, deposit1);

    timeProvider.advanceTimeByMillis(PublishOnInactivityDepositHandler.EVENT_TIMEOUT_MILLIS - 1);
    tick();
    verify(batcher, never()).publishPendingBlock();

    handler.onDepositEvent(block2, deposit2);
    timeProvider.advanceTimeByMillis(PublishOnInactivityDepositHandler.EVENT_TIMEOUT_MILLIS - 1);
    tick();
    verify(batcher, never()).publishPendingBlock();

    // And then publish when the timeout after the second event is reached
    timeProvider.advanceTimeByMillis(1);
    tick();
    verify(batcher).publishPendingBlock();
  }

  private void tick() {
    handler.onTick(new Date(timeProvider.getTimeInMillis().longValue()));
  }
}

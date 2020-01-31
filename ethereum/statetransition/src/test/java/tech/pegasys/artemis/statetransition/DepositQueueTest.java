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

package tech.pegasys.artemis.statetransition;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.statetransition.deposit.DepositQueue;

class DepositQueueTest {

  @SuppressWarnings("unchecked")
  private final Consumer<DepositsFromBlockEvent> output = mock(Consumer.class);

  private final InOrder inOrder = inOrder(output);

  private final DepositQueue depositQueue = new DepositQueue(output);

  @Test
  public void shouldPassThroughDepositWithNextIndexImmediately() {
    final DepositsFromBlockEvent deposit0 = eventWithSingleDeposit(0);
    final DepositsFromBlockEvent deposit1 = eventWithSingleDeposit(1);
    final DepositsFromBlockEvent deposit2 = eventWithSingleDeposit(2);
    depositQueue.onDeposit(deposit0);
    inOrder.verify(output).accept(deposit0);
    inOrder.verifyNoMoreInteractions();

    depositQueue.onDeposit(deposit1);
    inOrder.verify(output).accept(deposit1);
    inOrder.verifyNoMoreInteractions();

    depositQueue.onDeposit(deposit2);
    inOrder.verify(output).accept(deposit2);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldDelayDepositUntilEarlierIndicesHaveBeenReceived() {
    final DepositsFromBlockEvent event0 = eventWithDepositIndexRange(0, 5);
    final DepositsFromBlockEvent event1 = eventWithDepositIndexRange(6, 10);
    final DepositsFromBlockEvent event2 = eventWithDepositIndexRange(11, 15);
    depositQueue.onDeposit(event1);
    verifyNoInteractions(output);

    depositQueue.onDeposit(event2);
    verifyNoInteractions(output);

    depositQueue.onDeposit(event0);

    inOrder.verify(output).accept(event0);
    inOrder.verify(output).accept(event1);
    inOrder.verify(output).accept(event2);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldOutputDelayedDepositsAsSoonAsPossible() {
    final DepositsFromBlockEvent event0 = eventWithDepositIndexRange(0, 5);
    final DepositsFromBlockEvent event1 = eventWithDepositIndexRange(6, 10);
    final DepositsFromBlockEvent event2 = eventWithDepositIndexRange(11, 15);
    depositQueue.onDeposit(event1);
    verifyNoInteractions(output);

    depositQueue.onDeposit(event0);

    inOrder.verify(output).accept(event0);
    inOrder.verify(output).accept(event1);

    depositQueue.onDeposit(event2);
    inOrder.verify(output).accept(event2);
    inOrder.verifyNoMoreInteractions();
  }

  private DepositsFromBlockEvent eventWithSingleDeposit(int index) {
    return eventWithDepositIndexRange(index, index);
  }

  private DepositsFromBlockEvent eventWithDepositIndexRange(int start, int end) {
    final DepositsFromBlockEvent event = mock(DepositsFromBlockEvent.class);
    when(event.getFirstDepositIndex()).thenReturn(UnsignedLong.valueOf(start));
    when(event.getLastDepositIndex()).thenReturn(UnsignedLong.valueOf(end));
    return event;
  }
}

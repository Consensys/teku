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

import com.google.common.primitives.UnsignedLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;

class DepositQueueTest {
  private int seed = 4892424;

  @SuppressWarnings("unchecked")
  private final Consumer<DepositWithIndex> output = mock(Consumer.class);

  private final InOrder inOrder = inOrder(output);

  private final DepositQueue depositQueue = new DepositQueue(output);
  private final DepositWithIndex deposit0 = depositWithIndex(0);
  private final DepositWithIndex deposit1 = depositWithIndex(1);
  private final DepositWithIndex deposit2 = depositWithIndex(2);

  @Test
  public void shouldPassThroughDepositWithNextIndexImmediately() {
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
    depositQueue.onDeposit(deposit1);
    verifyNoInteractions(output);

    depositQueue.onDeposit(deposit2);
    verifyNoInteractions(output);

    depositQueue.onDeposit(deposit0);

    inOrder.verify(output).accept(deposit0);
    inOrder.verify(output).accept(deposit1);
    inOrder.verify(output).accept(deposit2);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldOutputDelayedDepositsAsSoonAsPossible() {
    depositQueue.onDeposit(deposit1);
    verifyNoInteractions(output);

    depositQueue.onDeposit(deposit0);

    inOrder.verify(output).accept(deposit0);
    inOrder.verify(output).accept(deposit1);

    depositQueue.onDeposit(deposit2);
    inOrder.verify(output).accept(deposit2);
    inOrder.verifyNoMoreInteractions();
  }

  private DepositWithIndex depositWithIndex(final int index) {
    return new DepositWithIndex(
        DataStructureUtil.randomDepositData(seed++), UnsignedLong.valueOf(index));
  }
}

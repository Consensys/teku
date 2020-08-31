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

package tech.pegasys.teku.pow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.exception.InvalidDepositEventsException;

public class ValidatingEth1EventsPublisherTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final Eth1EventsChannel delegate = mock(Eth1EventsChannel.class);
  private final ValidatingEth1EventsPublisher publisher =
      new ValidatingEth1EventsPublisher(delegate);

  @Test
  public void onDepositsFromBlock_inOrder() {
    final DepositsFromBlockEvent event1 = dataStructureUtil.randomDepositsFromBlockEvent(1, 0, 10);
    final DepositsFromBlockEvent event2 = dataStructureUtil.randomDepositsFromBlockEvent(2, 10, 11);
    final DepositsFromBlockEvent event3 = dataStructureUtil.randomDepositsFromBlockEvent(3, 11, 15);

    publisher.onDepositsFromBlock(event1);
    verify(delegate).onDepositsFromBlock(event1);
    publisher.onDepositsFromBlock(event2);
    verify(delegate).onDepositsFromBlock(event2);
    publisher.onDepositsFromBlock(event3);
    verify(delegate).onDepositsFromBlock(event3);
  }

  @Test
  public void onDepositsFromBlock_missingDeposit() {
    final DepositsFromBlockEvent event1 = dataStructureUtil.randomDepositsFromBlockEvent(1, 0, 10);
    final DepositsFromBlockEvent event3 = dataStructureUtil.randomDepositsFromBlockEvent(3, 11, 15);

    publisher.onDepositsFromBlock(event1);
    assertThatThrownBy(() -> publisher.onDepositsFromBlock(event3))
        .isInstanceOf(InvalidDepositEventsException.class)
        .hasMessageContaining("Expected next deposit at index 10");
  }

  @Test
  public void onDepositsFromBlock_outOfOrder() {
    final DepositsFromBlockEvent event1 = dataStructureUtil.randomDepositsFromBlockEvent(1, 0, 10);
    final DepositsFromBlockEvent event3 = dataStructureUtil.randomDepositsFromBlockEvent(3, 11, 15);

    publisher.onDepositsFromBlock(event3);
    assertThatThrownBy(() -> publisher.onDepositsFromBlock(event1))
        .isInstanceOf(InvalidDepositEventsException.class)
        .hasMessageContaining("Expected next deposit at index 15");
  }

  @Test
  public void onDepositsFromBlock_noLatestIndexSet() {
    final DepositsFromBlockEvent event2 = dataStructureUtil.randomDepositsFromBlockEvent(2, 10, 11);
    final DepositsFromBlockEvent event3 = dataStructureUtil.randomDepositsFromBlockEvent(3, 11, 15);

    publisher.onDepositsFromBlock(event2);
    verify(delegate).onDepositsFromBlock(event2);
    publisher.onDepositsFromBlock(event3);
    verify(delegate).onDepositsFromBlock(event3);
  }

  @Test
  public void onDepositsFromBlock_latestIndexSetConsistently() {
    final DepositsFromBlockEvent event2 = dataStructureUtil.randomDepositsFromBlockEvent(2, 10, 11);
    final DepositsFromBlockEvent event3 = dataStructureUtil.randomDepositsFromBlockEvent(3, 11, 15);

    publisher.setLastestPublishedDeposit(UInt64.valueOf(9));
    publisher.onDepositsFromBlock(event2);
    verify(delegate).onDepositsFromBlock(event2);
    publisher.onDepositsFromBlock(event3);
    verify(delegate).onDepositsFromBlock(event3);
  }

  @Test
  public void onDepositsFromBlock_latestIndexSet_missingEvent() {
    final DepositsFromBlockEvent event2 = dataStructureUtil.randomDepositsFromBlockEvent(2, 10, 11);

    publisher.setLastestPublishedDeposit(UInt64.valueOf(8));
    assertThatThrownBy(() -> publisher.onDepositsFromBlock(event2))
        .isInstanceOf(InvalidDepositEventsException.class)
        .hasMessageContaining("Expected next deposit at index 9");
  }

  @Test
  public void onDepositsFromBlock_latestIndexSet_duplicateEvent() {
    final DepositsFromBlockEvent event2 = dataStructureUtil.randomDepositsFromBlockEvent(2, 10, 11);

    publisher.setLastestPublishedDeposit(event2.getLastDepositIndex());
    assertThatThrownBy(() -> publisher.onDepositsFromBlock(event2))
        .isInstanceOf(InvalidDepositEventsException.class)
        .hasMessageContaining("Expected next deposit at index 11");
  }

  @Test
  public void setLastestPublishedDeposit_afterEventProcessed() {
    final DepositsFromBlockEvent event1 = dataStructureUtil.randomDepositsFromBlockEvent(1, 0, 10);

    publisher.onDepositsFromBlock(event1);
    assertThatThrownBy(() -> publisher.setLastestPublishedDeposit(event1.getLastDepositIndex()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Latest published deposit is already set");
  }

  @Test
  public void setLastestPublishedDeposit_setTwice() {
    publisher.setLastestPublishedDeposit(UInt64.ZERO);
    assertThatThrownBy(() -> publisher.setLastestPublishedDeposit(UInt64.ONE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Latest published deposit is already set");
  }
}

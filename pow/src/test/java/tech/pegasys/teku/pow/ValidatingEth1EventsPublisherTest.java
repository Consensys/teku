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
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.InvalidDepositEventsException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.spec.util.DataStructureUtil;

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
        .hasMessageContaining("Expected next deposit at index 10, but got 11");
  }

  @Test
  public void onDepositsFromBlock_noLatestIndexSet_depositsFromZero() {
    final DepositsFromBlockEvent event1 = dataStructureUtil.randomDepositsFromBlockEvent(1, 0, 10);
    final DepositsFromBlockEvent event2 = dataStructureUtil.randomDepositsFromBlockEvent(2, 10, 11);

    publisher.onDepositsFromBlock(event1);
    verify(delegate).onDepositsFromBlock(event1);
    publisher.onDepositsFromBlock(event2);
    verify(delegate).onDepositsFromBlock(event2);
  }

  @Test
  public void onDepositsFromBlock_noLatestIndexSet_depositsAfterZero() {
    final DepositsFromBlockEvent event2 = dataStructureUtil.randomDepositsFromBlockEvent(2, 10, 11);

    assertThatThrownBy(() -> publisher.onDepositsFromBlock(event2))
        .isInstanceOf(InvalidDepositEventsException.class)
        .hasMessageContaining("Expected next deposit at index 0, but got 10");
  }

  @Test
  public void onDepositsFromBlock_latestIndexSetConsistently() {
    final DepositsFromBlockEvent event2 = dataStructureUtil.randomDepositsFromBlockEvent(2, 10, 11);
    final DepositsFromBlockEvent event3 = dataStructureUtil.randomDepositsFromBlockEvent(3, 11, 15);

    publisher.setLatestPublishedDeposit(UInt64.valueOf(9));
    publisher.onDepositsFromBlock(event2);
    verify(delegate).onDepositsFromBlock(event2);
    publisher.onDepositsFromBlock(event3);
    verify(delegate).onDepositsFromBlock(event3);
  }

  @Test
  public void onDepositsFromBlock_latestIndexSet_missingEvent() {
    final DepositsFromBlockEvent event2 = dataStructureUtil.randomDepositsFromBlockEvent(2, 10, 11);

    publisher.setLatestPublishedDeposit(UInt64.valueOf(8));
    assertThatThrownBy(() -> publisher.onDepositsFromBlock(event2))
        .isInstanceOf(InvalidDepositEventsException.class)
        .hasMessageContaining("Expected next deposit at index 9, but got 10");
  }

  @Test
  public void onDepositsFromBlock_latestIndexSet_duplicateEvent() {
    final DepositsFromBlockEvent event2 = dataStructureUtil.randomDepositsFromBlockEvent(2, 10, 11);

    publisher.setLatestPublishedDeposit(event2.getLastDepositIndex());
    assertThatThrownBy(() -> publisher.onDepositsFromBlock(event2))
        .isInstanceOf(InvalidDepositEventsException.class)
        .hasMessageContaining("Expected next deposit at index 11, but got 10");
  }

  @Test
  public void setLastestPublishedDeposit_afterEventProcessed() {
    final DepositsFromBlockEvent event1 = dataStructureUtil.randomDepositsFromBlockEvent(1, 0, 10);

    publisher.onDepositsFromBlock(event1);
    assertThatThrownBy(() -> publisher.setLatestPublishedDeposit(event1.getLastDepositIndex()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Latest published deposit is already set");
  }

  @Test
  public void setLastestPublishedDeposit_setTwice() {
    publisher.setLatestPublishedDeposit(UInt64.ZERO);
    assertThatThrownBy(() -> publisher.setLatestPublishedDeposit(UInt64.ONE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Latest published deposit is already set");
  }
}

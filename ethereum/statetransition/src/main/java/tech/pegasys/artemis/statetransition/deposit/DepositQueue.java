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

package tech.pegasys.artemis.statetransition.deposit;

import com.google.common.primitives.UnsignedLong;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;

public class DepositQueue {
  private static final Logger LOG = LogManager.getLogger();

  private final NavigableSet<DepositsFromBlockEvent> pendingDeposits =
      new TreeSet<>(Comparator.comparing(DepositsFromBlockEvent::getFirstDepositIndex));
  private final Consumer<DepositsFromBlockEvent> depositConsumer;
  private UnsignedLong expectedDepositIndex = UnsignedLong.ZERO;

  public DepositQueue(final Consumer<DepositsFromBlockEvent> depositConsumer) {
    this.depositConsumer = depositConsumer;
  }

  public void onDeposit(final DepositsFromBlockEvent deposit) {
    LOG.trace("New deposits received from block {}", deposit.getBlockNumber());
    pendingDeposits.add(deposit);
    processPendingDeposits();
  }

  private void processPendingDeposits() {
    for (Iterator<DepositsFromBlockEvent> i = pendingDeposits.iterator(); i.hasNext(); ) {
      final DepositsFromBlockEvent deposits = i.next();
      if (!deposits.getFirstDepositIndex().equals(expectedDepositIndex)) {
        return;
      }
      LOG.trace("Processing deposits from block {}", deposits.getBlockNumber());
      depositConsumer.accept(deposits);
      i.remove();
      expectedDepositIndex = deposits.getLastDepositIndex().plus(UnsignedLong.ONE);
    }
  }
}

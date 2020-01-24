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

import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import com.google.common.primitives.UnsignedLong;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;

public class DepositQueue {

  private final NavigableSet<DepositWithIndex> pendingDeposits = new TreeSet<>();
  private final Consumer<DepositWithIndex> depositConsumer;
  private UnsignedLong expectedDepositIndex = UnsignedLong.ZERO;

  public DepositQueue(final Consumer<DepositWithIndex> depositConsumer) {
    this.depositConsumer = depositConsumer;
  }

  public void onDeposit(DepositWithIndex deposit) {
    STDOUT.log(Level.DEBUG, "New deposit received");
    pendingDeposits.add(deposit);
    processPendingDeposits();
  }

  private void processPendingDeposits() {
    for (Iterator<DepositWithIndex> i = pendingDeposits.iterator(); i.hasNext(); ) {
      final DepositWithIndex depositToProcess = i.next();
      if (!depositToProcess.getIndex().equals(expectedDepositIndex)) {
        return;
      }
      depositConsumer.accept(depositToProcess);
      i.remove();
      expectedDepositIndex = expectedDepositIndex.plus(UnsignedLong.ONE);
    }
  }
}

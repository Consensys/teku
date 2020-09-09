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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.exception.InvalidDepositEventsException;

public class ValidatingEth1EventsPublisher extends DelegatingEth1EventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  private Optional<UInt64> lastPublishedDeposit = Optional.empty();

  public ValidatingEth1EventsPublisher(final Eth1EventsChannel delegate) {
    super(delegate);
  }

  public synchronized void setLatestPublishedDeposit(final UInt64 latestPublishedDeposit) {
    checkNotNull(latestPublishedDeposit);
    if (!lastPublishedDeposit.isEmpty()) {
      throw new IllegalStateException("Latest published deposit is already set");
    }
    this.lastPublishedDeposit = Optional.of(latestPublishedDeposit);
  }

  @Override
  public synchronized void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    LOG.trace(
        "Process deposits {} - {}", event.getFirstDepositIndex(), event.getLastDepositIndex());
    assertDepositEventIsValid(event);
    lastPublishedDeposit = Optional.of(event.getLastDepositIndex());

    delegate.onDepositsFromBlock(event);
  }

  private void assertDepositEventIsValid(final DepositsFromBlockEvent event) {
    final UInt64 expectedFirstDepositIndex =
        lastPublishedDeposit.map(UInt64::increment).orElse(UInt64.ZERO);
    if (!expectedFirstDepositIndex.equals(event.getFirstDepositIndex())) {
      throw InvalidDepositEventsException.expectedDepositAtIndex(
          expectedFirstDepositIndex, event.getFirstDepositIndex());
    }
  }
}

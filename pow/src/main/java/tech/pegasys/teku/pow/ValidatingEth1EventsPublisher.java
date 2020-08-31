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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.exception.InvalidDepositEventsException;

public class ValidatingEth1EventsPublisher extends DelegatingEth1EventsChannel {
  private Optional<UInt64> lastPublishedDeposit = Optional.empty();

  public ValidatingEth1EventsPublisher(final Eth1EventsChannel delegate) {
    super(delegate);
  }

  @Override
  public synchronized void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    assertDepositEventIsValid(event);
    lastPublishedDeposit = Optional.of(event.getLastDepositIndex());

    delegate.onDepositsFromBlock(event);
  }

  private void assertDepositEventIsValid(final DepositsFromBlockEvent event) {
    final Optional<UInt64> expectedFirstDepositIndex = lastPublishedDeposit.map(UInt64::increment);
    final boolean depositIndexIsValid =
        expectedFirstDepositIndex
            .map(expected -> expected.equals(event.getFirstDepositIndex()))
            .orElse(true);

    if (!depositIndexIsValid) {
      throw InvalidDepositEventsException.expectedDepositAtIndex(expectedFirstDepositIndex.get());
    }
  }
}

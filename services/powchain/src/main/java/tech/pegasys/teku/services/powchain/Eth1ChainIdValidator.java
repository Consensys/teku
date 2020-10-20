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

package tech.pegasys.teku.services.powchain;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.pow.Eth1Provider;
import tech.pegasys.teku.util.config.Constants;

public class Eth1ChainIdValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth1Provider eth1Provider;

  private final AsyncRunner asyncRunner;

  private final AtomicBoolean stopped = new AtomicBoolean(false);

  public Eth1ChainIdValidator(Eth1Provider eth1Provider, AsyncRunner asyncRunner) {
    this.eth1Provider = eth1Provider;
    this.asyncRunner = asyncRunner;
  }

  public void start() {
    validate();
  }

  public void stop() {
    stopped.set(true);
  }

  private void validate() {
    if (stopped.get()) {
      return;
    }

    eth1Provider
        .getChainId()
        .whenException(err -> LOG.debug("Failed to get Eth1 chain id. Will retry.", err))
        .thenAccept(
            chainId -> {
              if (chainId.intValueExact() != Constants.DEPOSIT_CHAIN_ID) {
                STATUS_LOG.eth1DepositChainIdMismatch(
                    Constants.DEPOSIT_CHAIN_ID, chainId.intValueExact());
              }
            })
        .handleComposed((__, err) -> asyncRunner.runAfterDelay(this::validate, 1, TimeUnit.MINUTES))
        .reportExceptions();
  }
}

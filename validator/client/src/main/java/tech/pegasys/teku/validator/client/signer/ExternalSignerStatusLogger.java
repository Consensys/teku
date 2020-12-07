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

package tech.pegasys.teku.validator.client.signer;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;

public class ExternalSignerStatusLogger {
  private static final Logger LOG = LogManager.getLogger();
  private static final long DELAY_SECONDS = 30;
  private final BooleanSupplier upcheckSupplier;
  private final URL signingServiceUrl;
  private final AsyncRunner asyncRunner;
  private final AtomicBoolean isReachable = new AtomicBoolean(false);

  public ExternalSignerStatusLogger(
      final BooleanSupplier upcheckSupplier,
      final URL signingServiceUrl,
      final AsyncRunner asyncRunner) {
    this.upcheckSupplier = upcheckSupplier;
    this.signingServiceUrl = signingServiceUrl;
    this.asyncRunner = asyncRunner;
  }

  public void logWithFixedDelay() {
    asyncRunner.runWithFixedDelay(
        this::log,
        DELAY_SECONDS,
        TimeUnit.SECONDS,
        err -> LOG.debug("Unexpected error calling external signer upcheck", err));
  }

  public void log() {
    final boolean upcheckStatus = upcheckSupplier.getAsBoolean();
    if (upcheckStatus) {
      if (isReachable.compareAndSet(false, true)) {
        STATUS_LOG.externalSignerStatus(signingServiceUrl, true);
      }
    } else {
      isReachable.set(false);
      STATUS_LOG.externalSignerStatus(signingServiceUrl, false);
    }
  }
}

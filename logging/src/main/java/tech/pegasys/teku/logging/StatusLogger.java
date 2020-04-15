/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.logging;

import static tech.pegasys.teku.logging.LoggingConfigurator.STATUS_LOGGER_NAME;

import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StatusLogger {

  public static final StatusLogger STATUS_LOG = new StatusLogger(STATUS_LOGGER_NAME);

  private final Logger log;

  private StatusLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void specificationFailure(final String description, final Throwable cause) {
    log.warn("Spec failed for {}: {}", description, cause, cause);
  }

  public void unexpectedFailure(final String description, final Throwable cause) {
    log.fatal(
        "PLEASE FIX OR REPORT | Unexpected exception thrown for {}: {}", cause, description, cause);
  }

  public void listeningForLibP2P(final String address) {
    log.info("Listening for connections on: {}", address);
  }

  public void blockCreationFailure(final Exception cause) {
    log.error("Error during block creation", cause);
  }

  public void attestationFailure(final Throwable cause) {
    log.error("Error during attestation creation", cause);
  }

  public void validatorDepositYamlKeyWriterFailure(final Path file) {
    log.error("Error writing keys to {}", file.toString());
  }

  public void validatorDepositEncryptedKeystoreWriterFailure(
      final String message, final Path file, final String cause) {
    log.error(message, file.toString(), cause);
  }

  public void beginInitializingChainData() {
    log.info("Begin initializing chain data from storage");
  }

  public void finishInitializingChainData() {
    log.info("Finish initializing chain data from storage");
  }
}

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
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StatusLogger {

  public static final StatusLogger STATUS_LOG = new StatusLogger(STATUS_LOGGER_NAME);

  private final Logger log;

  private StatusLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void fatalError(final String description, final Throwable cause) {
    log.fatal("Exiting due to fatal error in {}", description, cause);
  }

  public void specificationFailure(final String description, final Throwable cause) {
    log.warn("Spec failed for {}: {}", description, cause, cause);
  }

  public void unexpectedFailure(final String description, final Throwable cause) {
    log.error("PLEASE FIX OR REPORT | Unexpected exception thrown for {}", description, cause);
  }

  public void listeningForLibP2P(final String address) {
    log.info("Listening for connections on: {}", address);
  }

  public void listeningForDiscv5PreGenesis(final String enr) {
    log.info("PreGenesis Local ENR: {}", enr);
  }

  public void listeningForDiscv5(final String enr) {
    log.info("Local ENR: {}", enr);
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
    log.info("Initializing storage");
  }

  public void finishInitializingChainData() {
    log.info("Storage initialization complete");
  }

  public void generatingMockStartGenesis(final long genesisTime, final int size) {
    log.info(
        "Starting with mocked start interoperability mode with genesis time {} and {} validators",
        () -> DateFormatUtils.format(genesisTime * 1000, "yyyy-MM-dd hh:mm:ss"),
        () -> size);
  }

  public void loadingGenesisFile(final String genesisFile) {
    log.info("Loading genesis from {}", genesisFile);
  }

  public void loadingGenesisFromEth1Chain() {
    log.info("No genesis state available. Loading deposits from ETH1 chain");
  }

  public void genesisValidatorsActivated(int activeValidatorCount, int requiredValidatorCount) {
    log.info(
        "Activated {} of {} validators required for genesis ({}%)",
        activeValidatorCount,
        requiredValidatorCount,
        activeValidatorCount * 100 / requiredValidatorCount);
  }

  public void minGenesisTimeReached() {
    log.info("Minimum genesis time reached");
  }

  public void dataPathSet(final String dataPath) {
    log.info("Using data path: {}", dataPath);
  }
}

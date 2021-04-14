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

package tech.pegasys.teku.infrastructure.logging;

import static java.util.stream.Collectors.joining;
import static tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.print;

import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StatusLogger {

  public static final StatusLogger STATUS_LOG =
      new StatusLogger(LoggingConfigurator.STATUS_LOGGER_NAME);

  final Logger log;

  private StatusLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void onStartup(final String version) {
    log.info("Teku version: {}", version);
    log.info(
        "This software is licensed under the Apache License, Version 2.0 (the \"License\"); "
            + "you may not use this software except in compliance with the License. "
            + "You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0");
  }

  public void fatalError(final String description, final Throwable cause) {
    log.fatal("Exiting due to fatal error in {}", description, cause);
  }

  public void specificationFailure(final String description, final Throwable cause) {
    log.warn("Spec failed for {}: {}", description, cause, cause);
  }

  public void eth1DepositEventsFailure(final Throwable cause) {
    log.fatal(
        "PLEASE CHECK YOUR ETH1 NODE | Encountered a problem retrieving deposit events from eth1 endpoint.",
        cause);
  }

  public void eth1FetchDepositsRequiresSmallerRange(final int batchSize) {
    log.warn(
        "Request for eth1 deposit logs from {} blocks failed. Retrying with a smaller block range.",
        batchSize);
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

  public void loadingValidators(final int validatorCount) {
    log.info("Loading {} validator keys...", validatorCount);
  }

  public void atLoadedValidatorNumber(
      final int loadedValidatorCount, final int totalValidatorCount) {
    log.info("Loaded validator key {} of {}.", loadedValidatorCount, totalValidatorCount);
  }

  public void validatorsInitialised(final List<String> validators) {
    if (validators.size() > 100) {
      log.info("Loaded {} validators", validators.size());
      log.debug("validators: {}", () -> validators.stream().collect(joining(", ")));
    } else {
      log.info(
          "Loaded {} Validators: {}",
          validators::size,
          () -> validators.stream().collect(joining(", ")));
    }
  }

  public void beginInitializingChainData() {
    log.info("Initializing storage");
  }

  public void fatalErrorInitialisingStorage(Throwable err) {
    log.debug("Failed to intiailize storage", err);
    log.fatal(
        "Failed to initialize storage. "
            + "Check the existing database matches the current network configuration. "
            + "Set log level to debug for more information.");
  }

  public void finishInitializingChainData() {
    log.info("Storage initialization complete");
  }

  public void recordedFinalizedBlocks(final int numberRecorded, final int totalToRecord) {
    log.info("Recorded {} of {} finalized blocks", numberRecorded, totalToRecord);
  }

  public void generatingMockStartGenesis(final long genesisTime, final int size) {
    log.info(
        "Starting with mocked start interoperability mode with genesis time {} and {} validators",
        () -> DateFormatUtils.format(genesisTime * 1000, "yyyy-MM-dd hh:mm:ss"),
        () -> size);
  }

  public void timeUntilGenesis(final long timeToGenesis, final int peerCount) {
    log.info(
        "{} until genesis time is reached. Peers: {}",
        () -> DurationFormatUtils.formatDurationWords(timeToGenesis * 1000, true, true),
        () -> peerCount);
  }

  public void loadingInitialStateResource(final String wsBlockResource) {
    log.info("Loading initial state from {}", wsBlockResource);
  }

  public void loadedInitialStateResource(
      final Bytes32 stateRoot,
      final Bytes32 blockRoot,
      final UInt64 blockSlot,
      final UInt64 epoch,
      final UInt64 epochStartSlot) {
    if (blockSlot.isGreaterThan(0)) {
      log.info(
          "Loaded initial state at epoch {} (state root = {}, block root = {}, block slot = {}).  Please ensure that the supplied initial state corresponds to the latest finalized block as of the start of epoch {} (slot {}).",
          epoch,
          stateRoot,
          blockRoot,
          blockSlot,
          epoch,
          epochStartSlot);
    } else {
      log.info(
          "Loaded initial state at epoch {} (state root = {}, block root = {}, block slot = {}).",
          epoch,
          stateRoot,
          blockRoot,
          blockSlot);
    }
  }

  public void warnInitialStateIgnored() {
    log.warn("Not loading specified initial state as chain data already exists.");
  }

  public void warnOnInitialStateWithSkippedSlots(
      final Level level,
      final UInt64 anchorBlockSlot,
      final UInt64 anchorEpoch,
      final UInt64 anchorEpochStartSlot) {
    final UInt64 slotsBetweenBlockAndEpochStart =
        anchorEpochStartSlot.minusMinZero(anchorBlockSlot);
    if (slotsBetweenBlockAndEpochStart.equals(UInt64.ZERO)) {
      return;
    }

    final String msg =
        String.format(
            "The provided initial state is %s slots prior to the start of epoch %s. Please ensure that slots %s - %s (inclusive) are empty.",
            slotsBetweenBlockAndEpochStart,
            anchorEpoch,
            anchorBlockSlot.plus(1),
            anchorEpochStartSlot);
    logWithColorIfLevelGreaterThanInfo(level, msg, ColorConsolePrinter.Color.YELLOW);
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
    log.info("ETH1 block satisfying minimum genesis time found");
  }

  public void migratingDataDirectory(final Path dataPath) {
    log.info(
        "Migrating data directory layout under {} to separate beacon node and validator data",
        dataPath.toAbsolutePath());
  }

  public void beaconDataPathSet(final Path dataPath) {
    log.info("Storing beacon chain data in: {}", dataPath.toAbsolutePath());
  }

  public void validatorDataPathSet(final Path dataPath) {
    log.info("Storing validator data in: {}", dataPath.toAbsolutePath());
  }

  public void eth1ServiceDown(final long interval) {
    log.warn("Eth1 service down for {}s, retrying", interval);
  }

  public void eth1AtHead(final BigInteger headBlockNumber) {
    log.info("Successfully loaded deposits up to Eth1 block {}", headBlockNumber);
  }

  public void usingGeneratedP2pPrivateKey(final String key, final boolean justGenerated) {
    if (justGenerated) {
      log.info("Generated new p2p private key and storing in: " + key);
    } else {
      log.info("Loading generated p2p private key from: " + key);
    }
  }

  public void adjustingP2pLowerBoundToUpperBound(final int p2pUpperBound) {
    log.info(
        "Adjusting target number of peers lower bound to equal upper bound, which is {}",
        p2pUpperBound);
  }

  public void adjustingP2pUpperBoundToLowerBound(final int p2pLowerBound) {
    log.info(
        "Adjusting target number of peers upper bound to equal lower bound, which is {}",
        p2pLowerBound);
  }

  public void performance(final String performance) {
    log.info(performance);
  }

  public void eth1DepositChainIdMismatch(int expectedChainId, int eth1ChainId) {
    log.log(
        Level.ERROR,
        "PLEASE CHECK YOUR ETH1 NODE | Wrong Eth1 chain id (expected={}, actual={})",
        expectedChainId,
        eth1ChainId);
  }

  public void externalSignerStatus(final URL externalSignerUrl, boolean isReachable) {
    if (isReachable) {
      log.info("External signer is reachable at {}", externalSignerUrl);
    } else {
      log.error(
          ColorConsolePrinter.print(
              "External signer is currently not reachable at " + externalSignerUrl,
              ColorConsolePrinter.Color.RED));
    }
  }

  public void unableToRetrieveValidatorStatusesFromBeaconNode() {
    log.error("Unable to retrieve validator statuses from BeaconNode.");
  }

  public void validatorStatus(String publicKey, String validatorStatus) {
    log.info("Validator {} status is {}.", publicKey, validatorStatus);
  }

  public void unableToRetrieveValidatorStatus(String publicKey) {
    log.warn("Unable to retrieve status for validator {}.", publicKey);
  }

  public void unableToRetrieveValidatorStatusSummary(int n) {
    log.warn("Unable to retrieve status for {} validators.", n);
  }

  public void validatorStatusSummary(int n, String validatorStatus) {
    log.info("{} validators are in {} state.", n, validatorStatus);
  }

  public void validatorStatusChange(String oldStatus, String newStatus, String publicKey) {
    log.warn("Validator {} has changed status from {} to {}.", publicKey, oldStatus, newStatus);
  }

  private void logWithColorIfLevelGreaterThanInfo(
      final Level level, final String msg, final ColorConsolePrinter.Color color) {
    final boolean useColor = level.compareTo(Level.INFO) < 0;
    log.log(level, useColor ? print(msg, color) : msg);
  }
}

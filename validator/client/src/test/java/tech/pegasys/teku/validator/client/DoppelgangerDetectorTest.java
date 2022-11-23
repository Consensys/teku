/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import nl.altindag.log.LogCaptor;
import nl.altindag.log.model.LogEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;

public class DoppelgangerDetectorTest {
  private final StatusLogger statusLog = mock(StatusLogger.class);
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final GenesisDataProvider genesisDataProvider = mock(GenesisDataProvider.class);
  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private static LogCaptor logCaptor;
  private final String doppelgangerStartLog = "Starting doppelganger detection...";
  private final String doppelgangerTimeoutLog =
      "Validators Doppelganger Detection timeout reached. Some technical issues prevented the validators doppelganger detection from running correctly. Please check the logs and consider performing a new validators doppelganger check.";

  private final Duration checkDelay = Duration.ofSeconds(2);
  private final Duration timeout = Duration.ofMinutes(15);

  private DoppelgangerDetector doppelgangerDetector;

  @BeforeAll
  public static void setupLogCaptor() {
    logCaptor = LogCaptor.forClass(DoppelgangerDetector.class);
  }

  @BeforeEach
  public void setup() {
    logCaptor.clearLogs();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(UInt64.ZERO));
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.completedFuture(IntArrayList.of(1, 2, 3)));
    doppelgangerDetector =
        new DoppelgangerDetector(
            statusLog,
            asyncRunner,
            validatorApiChannel,
            validatorIndexProvider,
            spec,
            timeProvider,
            genesisDataProvider,
            checkDelay,
            timeout);
  }

  @AfterAll
  public static void tearDown() {
    logCaptor.close();
  }

  @Test
  public void shouldNotDetectDoppelganger() {
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(4), dataStructureUtil.randomEpoch(), true),
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(5), dataStructureUtil.randomEpoch(), true)))));
    SafeFuture<Map<Integer, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection();
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(120);
    asyncRunner.executeQueuedActions();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(5);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerStartLog);
    expectLogMessage(logCaptor.getLogEvents().get(1), "INFO", doppelgangerDetectorStartEpochLog(0));
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", performingDoppelgangerCheckLog(0, 1));
    expectLogMessage(
        logCaptor.getLogEvents().get(3),
        "INFO",
        "No validators doppelganger detected for epoch 0, slot 1");
    expectLogMessage(
        logCaptor.getLogEvents().get(4),
        "INFO",
        "No validators doppelganger detected after 2 epochs. Stopping doppelganger detection.");
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldDetectDoppelgangerAndReturnIndicesOnly() {
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(1), dataStructureUtil.randomEpoch(), true),
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(3), dataStructureUtil.randomEpoch(), true)))));
    when(validatorIndexProvider.getValidatorIndicesByPublicKey())
        .thenReturn(SafeFuture.failedFuture(new Exception("Validators Public Keys Exception")));
    SafeFuture<Map<Integer, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection();
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(120);
    asyncRunner.executeQueuedActions();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(5);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerStartLog);
    expectLogMessage(logCaptor.getLogEvents().get(1), "INFO", doppelgangerDetectorStartEpochLog(0));
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", performingDoppelgangerCheckLog(0, 1));
    expectLogMessage(
        logCaptor.getLogEvents().get(3),
        "ERROR",
        "Doppelganger detected. Shutting down Validator Client.");
    expectLogMessage(
        logCaptor.getLogEvents().get(4),
        "ERROR",
        "Unable to get doppelgangers public keys. Only indices are available: java.lang.Exception: Validators Public Keys Exception");
    Map<Integer, BLSPublicKey> doppelgangers =
        Map.ofEntries(Map.entry(1, BLSPublicKey.empty()), Map.entry(3, BLSPublicKey.empty()));
    verify(statusLog)
        .validatorsDoppelgangerDetected(
            doppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> "")));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(doppelgangers);
  }

  @Test
  public void shouldDetectDoppelgangerAndReturnIndicesAndPublicKeys() {
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(1), dataStructureUtil.randomEpoch(), true),
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(2), dataStructureUtil.randomEpoch(), false),
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(3), dataStructureUtil.randomEpoch(), true)))));
    BLSPublicKey pubKey1 = dataStructureUtil.randomPublicKey();
    BLSPublicKey pubKey3 = dataStructureUtil.randomPublicKey();
    when(validatorIndexProvider.getValidatorIndicesByPublicKey())
        .thenReturn(
            SafeFuture.completedFuture(
                Map.ofEntries(
                    Map.entry(pubKey1, 1),
                    Map.entry(dataStructureUtil.randomPublicKey(), 2),
                    Map.entry(pubKey3, 3),
                    Map.entry(dataStructureUtil.randomPublicKey(), 5),
                    Map.entry(dataStructureUtil.randomPublicKey(), 7))));
    SafeFuture<Map<Integer, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection();
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(120);
    asyncRunner.executeQueuedActions();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(4);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerStartLog);
    expectLogMessage(logCaptor.getLogEvents().get(1), "INFO", doppelgangerDetectorStartEpochLog(0));
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", performingDoppelgangerCheckLog(0, 1));
    expectLogMessage(
        logCaptor.getLogEvents().get(3),
        "ERROR",
        "Doppelganger detected. Shutting down Validator Client.");
    Map<Integer, BLSPublicKey> doppelgangers =
        Map.ofEntries(Map.entry(1, pubKey1), Map.entry(3, pubKey3));
    verify(statusLog)
        .validatorsDoppelgangerDetected(
            doppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(doppelgangers);
  }

  @Test
  public void shouldTimeoutDueToGenesisDataProviderException() {
    when(genesisDataProvider.getGenesisTime())
        .thenReturn(SafeFuture.failedFuture(new Exception("Genesis Time Exception")));
    SafeFuture<Map<Integer, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection();
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBy(Duration.ofMinutes(25));
    asyncRunner.executeQueuedActions();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(3);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerStartLog);
    final String expectedErrorLog =
        "Unable to check validators doppelganger. Unable to get genesis time to calculate the current epoch: java.lang.Exception: Genesis Time Exception";
    expectLogMessage(logCaptor.getLogEvents().get(1), "ERROR", expectedErrorLog);
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", doppelgangerTimeoutLog);
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldTimeoutDueToValidatorIndexProviderException() {
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(UInt64.ZERO));
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.failedFuture(new Exception("Validator Indices Exception")));
    SafeFuture<Map<Integer, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection();
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBy(Duration.ofMinutes(25));
    asyncRunner.executeQueuedActions();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(5);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerStartLog);
    expectLogMessage(logCaptor.getLogEvents().get(1), "INFO", doppelgangerDetectorStartEpochLog(0));
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", performingDoppelgangerCheckLog(0, 1));
    final String expectedErrorLog =
        "Unable to check validators doppelganger. Unable to get validators indices: java.lang.Exception: Validator Indices Exception";
    expectLogMessage(logCaptor.getLogEvents().get(3), "ERROR", expectedErrorLog);
    expectLogMessage(logCaptor.getLogEvents().get(4), "INFO", doppelgangerTimeoutLog);
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldTimeoutDueToValidatorApiChannelException() {
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new Exception("Validator API Channel Exception")));
    SafeFuture<Map<Integer, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection();
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBy(Duration.ofMinutes(25));
    asyncRunner.executeQueuedActions();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(5);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerStartLog);
    expectLogMessage(logCaptor.getLogEvents().get(1), "INFO", doppelgangerDetectorStartEpochLog(0));
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", performingDoppelgangerCheckLog(0, 1));
    final String expectedErrorLog =
        "Unable to check validators doppelganger. Unable to get validators liveness: java.lang.Exception: Validator API Channel Exception";
    expectLogMessage(logCaptor.getLogEvents().get(3), "ERROR", expectedErrorLog);
    expectLogMessage(logCaptor.getLogEvents().get(4), "INFO", doppelgangerTimeoutLog);
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  private void expectLogMessage(
      LogEvent logEvent, String expectedLevel, String expectedLogMessage) {
    assertThat(logEvent.getLevel()).isEqualTo(expectedLevel);
    assertThat(logEvent.getMessage()).isEqualTo(expectedLogMessage);
  }

  private String doppelgangerDetectorStartEpochLog(int epoch) {
    return String.format("Validators doppelganger check started at epoch %d", epoch);
  }

  private String performingDoppelgangerCheckLog(int epoch, int slot) {
    return String.format(
        "Performing a validators doppelganger check for epoch %d, slot %d", epoch, slot);
  }
}

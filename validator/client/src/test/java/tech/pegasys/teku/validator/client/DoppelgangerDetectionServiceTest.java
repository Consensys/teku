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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.altindag.log.LogCaptor;
import nl.altindag.log.model.LogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;

public class DoppelgangerDetectionServiceTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final GenesisDataProvider genesisDataProvider = mock(GenesisDataProvider.class);
  private final TimeProvider timeProvider = mock(TimeProvider.class);
  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private final LogCaptor logCaptor = LogCaptor.forClass(DoppelgangerDetectionService.class);
  private final ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
  private final String doppelgangerServiceStartLog = "Starting doppelganger detection service...";
  private final String doppelgangerTimeoutLog =
      "Validators Doppelganger Detection timeout reached, stopping the service. Some technical issues prevented the validators doppelganger detection from running correctly. Please check the logs and consider performing a new validators doppelganger check.";

  @BeforeEach
  public void setup() throws IOException {
    System.setOut(new PrintStream(stdOut));
  }

  @AfterEach
  void tearDown() {
    System.setOut(System.out);
  }

  @Test
  public void shouldNotDetectDoppelganger() {
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(UInt64.ZERO));
    when(timeProvider.getTimeInSeconds())
        .thenReturn(UInt64.valueOf(10))
        .thenReturn(UInt64.valueOf(1200));
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.completedFuture(IntArrayList.of(1, 2, 3)));
    when(validatorApiChannel.checkValidatorsDoppelganger(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(4), dataStructureUtil.randomEpoch(), true),
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(5), dataStructureUtil.randomEpoch(), true)))));
    DoppelgangerDetectionService doppelgangerDetectionService =
        new DoppelgangerDetectionService(
            asyncRunner,
            validatorApiChannel,
            validatorIndexProvider,
            spec,
            timeProvider,
            genesisDataProvider,
            Duration.ofSeconds(2),
            Duration.ofMinutes(2));
    SafeFuture<Boolean> doppelgangerDetectionFuture =
        doppelgangerDetectionService.performDoppelgangerDetection();
    doppelgangerDetectionFuture.join();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(5);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerServiceStartLog);
    expectLogMessage(logCaptor.getLogEvents().get(1), "INFO", doppelgangerServiceStartEpochLog(0));
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", performingDoppelgangerCheckLog(0, 1));
    expectLogMessage(logCaptor.getLogEvents().get(3), "INFO", noDoppelgangerLog(0, 1));
    expectLogMessage(
        logCaptor.getLogEvents().get(4),
        "INFO",
        "No validators doppelganger detected after 2 epochs. Stopping doppelganger detection service.");
    assertThat(doppelgangerDetectionFuture).isCompleted();
    assertThat(doppelgangerDetectionFuture).isCompletedWithValue(Boolean.FALSE);
  }

  @Test
  public void shouldDetectDoppelgangerAndReturnIndicesOnly() {
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(UInt64.ZERO));
    when(timeProvider.getTimeInSeconds())
        .thenReturn(UInt64.valueOf(10))
        .thenReturn(UInt64.valueOf(1200));
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.completedFuture(IntArrayList.of(1, 2, 3)));
    when(validatorApiChannel.checkValidatorsDoppelganger(any(), any()))
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
    DoppelgangerDetectionService doppelgangerDetectionService =
        new DoppelgangerDetectionService(
            asyncRunner,
            validatorApiChannel,
            validatorIndexProvider,
            spec,
            timeProvider,
            genesisDataProvider,
            Duration.ofSeconds(2),
            Duration.ofMinutes(20));
    SafeFuture<Boolean> doppelgangerDetectionFuture =
        doppelgangerDetectionService.performDoppelgangerDetection();
    doppelgangerDetectionFuture.join();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(5);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerServiceStartLog);
    expectLogMessage(logCaptor.getLogEvents().get(1), "INFO", doppelgangerServiceStartEpochLog(0));
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", performingDoppelgangerCheckLog(0, 1));
    expectLogMessage(
        logCaptor.getLogEvents().get(3),
        "ERROR",
        "Doppelganger detected. Shutting down Validator Client.");
    expectLogMessage(
        logCaptor.getLogEvents().get(4),
        "ERROR",
        "Unable to get doppelgangers public keys. Only indices are available: java.lang.Exception: Validators Public Keys Exception");
    assertThat(stdOut.toString(UTF_8))
        .contains("Detected 2 validators doppelganger: \n" + "Index: 1\n" + "Index: 3");
    assertThat(doppelgangerDetectionFuture).isCompleted();
    assertThat(doppelgangerDetectionFuture).isCompletedWithValue(Boolean.TRUE);
  }

  @Test
  public void shouldDetectDoppelgangerAndReturnIndicesAndPublicKeys() {
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(UInt64.ZERO));
    when(timeProvider.getTimeInSeconds())
        .thenReturn(UInt64.valueOf(10))
        .thenReturn(UInt64.valueOf(1200));
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.completedFuture(IntArrayList.of(1, 2, 3)));
    when(validatorApiChannel.checkValidatorsDoppelganger(any(), any()))
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
    DoppelgangerDetectionService doppelgangerDetectionService =
        new DoppelgangerDetectionService(
            asyncRunner,
            validatorApiChannel,
            validatorIndexProvider,
            spec,
            timeProvider,
            genesisDataProvider,
            Duration.ofSeconds(2),
            Duration.ofMinutes(20));
    SafeFuture<Boolean> doppelgangerDetectionFuture =
        doppelgangerDetectionService.performDoppelgangerDetection();
    doppelgangerDetectionFuture.join();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(4);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerServiceStartLog);
    expectLogMessage(logCaptor.getLogEvents().get(1), "INFO", doppelgangerServiceStartEpochLog(0));
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", performingDoppelgangerCheckLog(0, 1));
    expectLogMessage(
        logCaptor.getLogEvents().get(3),
        "ERROR",
        "Doppelganger detected. Shutting down Validator Client.");
    assertThat(stdOut.toString(UTF_8))
        .contains(
            "Detected 2 validators doppelganger: \n"
                + "Index: 1, Public key: "
                + pubKey1
                + "\n"
                + "Index: 3, Public key: "
                + pubKey3);
    assertThat(doppelgangerDetectionFuture).isCompleted();
    assertThat(doppelgangerDetectionFuture).isCompletedWithValue(Boolean.TRUE);
  }

  @Test
  public void shouldTimeoutDueToGenesisDataProviderException() {
    when(genesisDataProvider.getGenesisTime())
        .thenReturn(SafeFuture.failedFuture(new Exception("Genesis Time Exception")));
    DoppelgangerDetectionService doppelgangerDetectionService =
        new DoppelgangerDetectionService(
            asyncRunner,
            validatorApiChannel,
            validatorIndexProvider,
            spec,
            timeProvider,
            genesisDataProvider,
            Duration.ofSeconds(2),
            Duration.ofSeconds(6));
    SafeFuture<Boolean> doppelgangerDetectionFuture =
        doppelgangerDetectionService.performDoppelgangerDetection();
    doppelgangerDetectionFuture.join();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(4);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerServiceStartLog);
    final String expectedErrorLog =
        "Unable to check validators doppelganger. Unable to get genesis time to calculate the current epoch: java.lang.Exception: Genesis Time Exception";
    expectLogMessage(logCaptor.getLogEvents().get(1), "ERROR", expectedErrorLog);
    expectLogMessage(logCaptor.getLogEvents().get(2), "ERROR", expectedErrorLog);
    expectLogMessage(logCaptor.getLogEvents().get(3), "INFO", doppelgangerTimeoutLog);
    assertThat(doppelgangerDetectionFuture).isCompleted();
    assertThat(doppelgangerDetectionFuture).isCompletedWithValue(Boolean.FALSE);
  }

  @Test
  public void shouldTimeoutDueToValidatorIndexProviderException() {
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(UInt64.ZERO));
    when(timeProvider.getTimeInSeconds()).thenReturn(UInt64.valueOf(10));
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.failedFuture(new Exception("Validator Indices Exception")));
    DoppelgangerDetectionService doppelgangerDetectionService =
        new DoppelgangerDetectionService(
            asyncRunner,
            validatorApiChannel,
            validatorIndexProvider,
            spec,
            timeProvider,
            genesisDataProvider,
            Duration.ofSeconds(2),
            Duration.ofSeconds(6));
    SafeFuture<Boolean> doppelgangerDetectionFuture =
        doppelgangerDetectionService.performDoppelgangerDetection();
    doppelgangerDetectionFuture.join();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(7);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerServiceStartLog);
    expectLogMessage(logCaptor.getLogEvents().get(1), "INFO", doppelgangerServiceStartEpochLog(0));
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", performingDoppelgangerCheckLog(0, 1));
    final String expectedErrorLog =
        "Unable to check validators doppelganger. Unable to get validators indices: java.lang.Exception: Validator Indices Exception";
    expectLogMessage(logCaptor.getLogEvents().get(3), "ERROR", expectedErrorLog);
    expectLogMessage(logCaptor.getLogEvents().get(4), "INFO", performingDoppelgangerCheckLog(0, 1));
    expectLogMessage(logCaptor.getLogEvents().get(5), "ERROR", expectedErrorLog);
    expectLogMessage(logCaptor.getLogEvents().get(6), "INFO", doppelgangerTimeoutLog);
    assertThat(doppelgangerDetectionFuture).isCompleted();
    assertThat(doppelgangerDetectionFuture).isCompletedWithValue(Boolean.FALSE);
  }

  @Test
  public void shouldTimeoutDueToValidatorApiChannelException() {
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(UInt64.ZERO));
    when(timeProvider.getTimeInSeconds()).thenReturn(UInt64.valueOf(10));
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.completedFuture(IntArrayList.of(1, 2, 3)));
    when(validatorApiChannel.checkValidatorsDoppelganger(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new Exception("Validator API Channel Exception")));
    DoppelgangerDetectionService doppelgangerDetectionService =
        new DoppelgangerDetectionService(
            asyncRunner,
            validatorApiChannel,
            validatorIndexProvider,
            spec,
            timeProvider,
            genesisDataProvider,
            Duration.ofSeconds(2),
            Duration.ofSeconds(6));
    SafeFuture<Boolean> doppelgangerDetectionFuture =
        doppelgangerDetectionService.performDoppelgangerDetection();
    doppelgangerDetectionFuture.join();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(7);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerServiceStartLog);
    expectLogMessage(logCaptor.getLogEvents().get(1), "INFO", doppelgangerServiceStartEpochLog(0));
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", performingDoppelgangerCheckLog(0, 1));
    final String expectedErrorLog =
        "Unable to check validators doppelganger. Unable to get validators liveness: java.lang.Exception: Validator API Channel Exception";
    expectLogMessage(logCaptor.getLogEvents().get(3), "ERROR", expectedErrorLog);
    expectLogMessage(logCaptor.getLogEvents().get(4), "INFO", performingDoppelgangerCheckLog(0, 1));
    expectLogMessage(logCaptor.getLogEvents().get(5), "ERROR", expectedErrorLog);
    expectLogMessage(logCaptor.getLogEvents().get(6), "INFO", doppelgangerTimeoutLog);
    assertThat(doppelgangerDetectionFuture).isCompleted();
    assertThat(doppelgangerDetectionFuture).isCompletedWithValue(Boolean.FALSE);
  }

  @Test
  public void shouldTimeoutDueToValidatorApiChannelTimeout() {
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(UInt64.ZERO));
    when(timeProvider.getTimeInSeconds()).thenReturn(UInt64.valueOf(10));
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.completedFuture(IntArrayList.of(1, 2, 3)));
    when(validatorApiChannel.checkValidatorsDoppelganger(any(), any()))
        .thenReturn(SafeFuture.of(new SafeFuture<>().newIncompleteFuture()));
    DoppelgangerDetectionService doppelgangerDetectionService =
        new DoppelgangerDetectionService(
            asyncRunner,
            validatorApiChannel,
            validatorIndexProvider,
            spec,
            timeProvider,
            genesisDataProvider,
            Duration.ofSeconds(2),
            Duration.ofSeconds(6));
    SafeFuture<Boolean> doppelgangerDetectionFuture =
        doppelgangerDetectionService.performDoppelgangerDetection();
    doppelgangerDetectionFuture.join();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(5);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerServiceStartLog);
    expectLogMessage(logCaptor.getLogEvents().get(1), "INFO", doppelgangerServiceStartEpochLog(0));
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", performingDoppelgangerCheckLog(0, 1));
    final String expectedErrorLog =
        "Unable to check validators doppelganger. Unable to get validators liveness: Request timeout";
    expectLogMessage(logCaptor.getLogEvents().get(3), "ERROR", expectedErrorLog);
    expectLogMessage(logCaptor.getLogEvents().get(4), "INFO", doppelgangerTimeoutLog);
    assertThat(doppelgangerDetectionFuture).isCompleted();
    assertThat(doppelgangerDetectionFuture).isCompletedWithValue(Boolean.FALSE);
  }

  @Test
  public void shouldTimeoutDueToValidatorIndexProviderTimeout() {
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(UInt64.ZERO));
    when(timeProvider.getTimeInSeconds()).thenReturn(UInt64.valueOf(10));
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.of(new SafeFuture<>().newIncompleteFuture()));
    DoppelgangerDetectionService doppelgangerDetectionService =
        new DoppelgangerDetectionService(
            asyncRunner,
            validatorApiChannel,
            validatorIndexProvider,
            spec,
            timeProvider,
            genesisDataProvider,
            Duration.ofSeconds(2),
            Duration.ofSeconds(6));
    SafeFuture<Boolean> doppelgangerDetectionFuture =
        doppelgangerDetectionService.performDoppelgangerDetection();
    doppelgangerDetectionFuture.join();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(5);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerServiceStartLog);
    expectLogMessage(logCaptor.getLogEvents().get(1), "INFO", doppelgangerServiceStartEpochLog(0));
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", performingDoppelgangerCheckLog(0, 1));
    final String expectedErrorLog =
        "Unable to check validators doppelganger. Unable to get validators indices: Request timeout";
    expectLogMessage(logCaptor.getLogEvents().get(3), "ERROR", expectedErrorLog);
    expectLogMessage(logCaptor.getLogEvents().get(4), "INFO", doppelgangerTimeoutLog);
    assertThat(doppelgangerDetectionFuture).isCompleted();
    assertThat(doppelgangerDetectionFuture).isCompletedWithValue(Boolean.FALSE);
  }

  @Test
  public void shouldTimeoutDueToGenesisDataProviderTimeout() {
    when(genesisDataProvider.getGenesisTime())
        .thenReturn(SafeFuture.of(new SafeFuture<>().newIncompleteFuture()));
    DoppelgangerDetectionService doppelgangerDetectionService =
        new DoppelgangerDetectionService(
            asyncRunner,
            validatorApiChannel,
            validatorIndexProvider,
            spec,
            timeProvider,
            genesisDataProvider,
            Duration.ofSeconds(2),
            Duration.ofSeconds(6));
    SafeFuture<Boolean> doppelgangerDetectionFuture =
        doppelgangerDetectionService.performDoppelgangerDetection();
    doppelgangerDetectionFuture.join();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(3);
    expectLogMessage(logCaptor.getLogEvents().get(0), "INFO", doppelgangerServiceStartLog);
    final String expectedErrorLog =
        "Unable to check validators doppelganger. Unable to get genesis time to calculate the current epoch: Request timeout";
    expectLogMessage(logCaptor.getLogEvents().get(1), "ERROR", expectedErrorLog);
    expectLogMessage(logCaptor.getLogEvents().get(2), "INFO", doppelgangerTimeoutLog);
    assertThat(doppelgangerDetectionFuture).isCompleted();
    assertThat(doppelgangerDetectionFuture).isCompletedWithValue(Boolean.FALSE);
  }

  private void expectLogMessage(
      LogEvent logEvent, String expectedLevel, String expectedLogMessage) {
    assertThat(logEvent.getLevel()).isEqualTo(expectedLevel);
    assertThat(logEvent.getMessage()).isEqualTo(expectedLogMessage);
  }

  private String doppelgangerServiceStartEpochLog(int epoch) {
    return String.format("Validators doppelganger check started at epoch %d", epoch);
  }

  private String performingDoppelgangerCheckLog(int epoch, int slot) {
    return String.format(
        "Performing a validators doppelganger check for epoch %d, slot %d", epoch, slot);
  }

  private String noDoppelgangerLog(int epoch, int slot) {
    return String.format("No validators doppelganger detected for epoch %d, slot %d", epoch, slot);
  }
}

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
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
import tech.pegasys.teku.validator.client.doppelganger.DoppelgangerDetector;

public class DoppelgangerDetectorTest {
  private final StatusLogger statusLog = mock(StatusLogger.class);
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final GenesisDataProvider genesisDataProvider = mock(GenesisDataProvider.class);
  private LogCaptor logCaptor;
  private final String doppelgangerDetectedLog = "Validator doppelganger detected...";

  private final Duration checkDelay = Duration.ofSeconds(2);
  private final Duration timeout = Duration.ofMinutes(15);
  private final int maxEpochs = 2;
  private final BLSPublicKey pubKey1 = dataStructureUtil.randomPublicKey();
  private final BLSPublicKey pubKey2 = dataStructureUtil.randomPublicKey();
  private final BLSPublicKey pubKey3 = dataStructureUtil.randomPublicKey();
  private DoppelgangerDetector doppelgangerDetector;

  @BeforeEach
  public void setup() {
    logCaptor = LogCaptor.forClass(DoppelgangerDetector.class);
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(UInt64.ZERO));

    when(validatorApiChannel.getValidatorIndices(Set.of(pubKey1)))
        .thenReturn(SafeFuture.completedFuture(Map.ofEntries(Map.entry(pubKey1, 1))));

    when(validatorApiChannel.getValidatorIndices(Set.of(pubKey2)))
        .thenReturn(SafeFuture.completedFuture(Map.ofEntries(Map.entry(pubKey2, 2))));

    when(validatorApiChannel.getValidatorIndices(Set.of(pubKey3)))
        .thenReturn(SafeFuture.completedFuture(Map.ofEntries(Map.entry(pubKey3, 3))));

    when(validatorApiChannel.getValidatorIndices(Set.of(pubKey1, pubKey2, pubKey3)))
        .thenReturn(
            SafeFuture.completedFuture(
                Map.ofEntries(
                    Map.entry(pubKey1, 1), Map.entry(pubKey2, 2), Map.entry(pubKey3, 3))));

    doppelgangerDetector =
        new DoppelgangerDetector(
            statusLog,
            asyncRunner,
            validatorApiChannel,
            spec,
            timeProvider,
            genesisDataProvider,
            checkDelay,
            timeout,
            maxEpochs);
  }

  @AfterEach
  public void tearDown() {
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

    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(120);
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog).doppelgangerCheck(0, pubKeysStrings);
    verify(statusLog).doppelgangerDetectionEnd(pubKeysStrings);
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldDetectDoppelgangers() {
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
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(120);
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog).doppelgangerCheck(0, pubKeysStrings);
    logCaptor.assertFatalLog(doppelgangerDetectedLog);
    Map<UInt64, BLSPublicKey> doppelgangers =
        Map.ofEntries(Map.entry(UInt64.valueOf(1), pubKey1), Map.entry(UInt64.valueOf(3), pubKey3));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            doppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(doppelgangers);
  }

  @Test
  public void shouldNotDetectDoppelgangerSeparately() {
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(4), dataStructureUtil.randomEpoch(), true),
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(5), dataStructureUtil.randomEpoch(), true)))));

    SafeFuture<Map<UInt64, BLSPublicKey>> firstDoppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey1));
    SafeFuture<Map<UInt64, BLSPublicKey>> secondDoppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey2));
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(150);
    asyncRunner.executeUntilDone();
    verify(statusLog).doppelgangerDetectionStart(Set.of(pubKey1.toAbbreviatedString()));
    verify(statusLog).doppelgangerDetectionStart(Set.of(pubKey2.toAbbreviatedString()));
    verify(statusLog).doppelgangerCheck(0, Set.of(pubKey1.toAbbreviatedString()));
    verify(statusLog).doppelgangerCheck(0, Set.of(pubKey2.toAbbreviatedString()));
    verify(statusLog).doppelgangerDetectionEnd(Set.of(pubKey1.toAbbreviatedString()));
    verify(statusLog).doppelgangerDetectionEnd(Set.of(pubKey2.toAbbreviatedString()));
    assertThat(firstDoppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
    assertThat(secondDoppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldDetectedDoppelgangersSeparately() {
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
    SafeFuture<Map<UInt64, BLSPublicKey>> firstDoppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey1));
    SafeFuture<Map<UInt64, BLSPublicKey>> secondDoppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey3));
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(120);
    asyncRunner.executeQueuedActions();
    verify(statusLog).doppelgangerDetectionStart(Set.of(pubKey1.toAbbreviatedString()));
    verify(statusLog).doppelgangerDetectionStart(Set.of(pubKey3.toAbbreviatedString()));
    verify(statusLog).doppelgangerCheck(0, Set.of(pubKey1.toAbbreviatedString()));
    verify(statusLog).doppelgangerCheck(0, Set.of(pubKey3.toAbbreviatedString()));
    logCaptor.assertMessagesInOrder(Level.FATAL, doppelgangerDetectedLog, doppelgangerDetectedLog);
    Map<UInt64, BLSPublicKey> firstlyDetectedDoppelgangers =
        Map.ofEntries(Map.entry(UInt64.valueOf(1), pubKey1));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            firstlyDetectedDoppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(firstDoppelgangerDetectorFuture).isCompletedWithValue(firstlyDetectedDoppelgangers);
    Map<UInt64, BLSPublicKey> secondlyDetectedDoppelgangers =
        Map.ofEntries(Map.entry(UInt64.valueOf(3), pubKey3));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            secondlyDetectedDoppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(secondDoppelgangerDetectorFuture)
        .isCompletedWithValue(secondlyDetectedDoppelgangers);
  }

  @Test
  public void shouldDetectedDoppelgangersAndTimeoutSeparately() {
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

    final Exception validatorIndicesException = new Exception("Validator Indices Exception");
    when(validatorApiChannel.getValidatorIndices(Set.of(pubKey3)))
        .thenReturn(SafeFuture.failedFuture(validatorIndicesException));

    SafeFuture<Map<UInt64, BLSPublicKey>> firstDoppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey1));
    SafeFuture<Map<UInt64, BLSPublicKey>> secondDoppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey3));
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(50);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBy(Duration.ofMinutes(50));
    asyncRunner.executeQueuedActions();
    verify(statusLog).doppelgangerDetectionStart(Set.of(pubKey1.toAbbreviatedString()));
    verify(statusLog).doppelgangerDetectionStart(Set.of(pubKey3.toAbbreviatedString()));
    logCaptor.assertMessagesInOrder(Level.FATAL, doppelgangerDetectedLog);
    Map<UInt64, BLSPublicKey> firstlyDetectedDoppelgangers =
        Map.ofEntries(Map.entry(UInt64.valueOf(1), pubKey1));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            firstlyDetectedDoppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(firstDoppelgangerDetectorFuture).isCompletedWithValue(firstlyDetectedDoppelgangers);

    logCaptor.assertErrorLog(
        String.format(
            "Unable to check validators doppelgangers for keys %s. Unable to get validators indices: java.lang.Exception: %s",
            pubKey3.toAbbreviatedString(), validatorIndicesException.getMessage()));
    verify(statusLog).doppelgangerDetectionTimeout(Set.of(pubKey3.toAbbreviatedString()));
    assertThat(secondDoppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldTimeoutDueToGenesisDataProviderException() {
    when(genesisDataProvider.getGenesisTime())
        .thenReturn(SafeFuture.failedFuture(new Exception("Genesis Time Exception")));
    Set<BLSPublicKey> pubKeys =
        Set.of(dataStructureUtil.randomPublicKey(), dataStructureUtil.randomPublicKey());
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBy(Duration.ofMinutes(25));
    asyncRunner.executeQueuedActions();
    verify(statusLog)
        .doppelgangerDetectionStart(toAbbreviatedKeys(pubKeys).collect(Collectors.toSet()));
    final String expectedErrorLog =
        String.format(
            "Unable to check validators doppelgangers for keys %s. Unable to get genesis time to calculate the current epoch: java.lang.Exception: Genesis Time Exception",
            toAbbreviatedKeys(pubKeys).collect(Collectors.joining(", ")));
    logCaptor.assertErrorLog(expectedErrorLog);
    verify(statusLog)
        .doppelgangerDetectionTimeout(toAbbreviatedKeys(pubKeys).collect(Collectors.toSet()));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldTimeoutDueToValidatorsIndicesException() {
    final Exception validatorIndicesException = new Exception("Validator Indices Exception");
    when(validatorApiChannel.getValidatorIndices(any()))
        .thenReturn(SafeFuture.failedFuture(validatorIndicesException));
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBy(Duration.ofMinutes(25));
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog).doppelgangerCheck(0, pubKeysStrings);
    logCaptor.assertErrorLog(
        String.format(
            "Unable to check validators doppelgangers for keys %s. Unable to get validators indices: java.lang.Exception: %s",
            toAbbreviatedKeys(pubKeys).collect(Collectors.joining(", ")),
            validatorIndicesException.getMessage()));
    verify(statusLog)
        .doppelgangerDetectionTimeout(toAbbreviatedKeys(pubKeys).collect(Collectors.toSet()));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldTimeoutDueToValidatorsLivenessException() {
    final Exception validatorLivenessException = new Exception("Validator Liveness Exception");
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(SafeFuture.failedFuture(validatorLivenessException));
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBy(Duration.ofMinutes(25));
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog).doppelgangerCheck(0, pubKeysStrings);
    logCaptor.assertErrorLog(
        String.format(
            "Unable to check validators doppelgangers for keys %s. Unable to get validators liveness: java.lang.Exception: %s",
            toAbbreviatedKeys(pubKeys).collect(Collectors.joining(", ")),
            validatorLivenessException.getMessage()));
    verify(statusLog)
        .doppelgangerDetectionTimeout(toAbbreviatedKeys(pubKeys).collect(Collectors.toSet()));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldSkipCheckWhenNoIndices() {
    when(validatorApiChannel.getValidatorIndices(Set.of(pubKey1, pubKey2, pubKey3)))
        .thenReturn(SafeFuture.completedFuture(new HashMap<>()));
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(120);
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog).doppelgangerCheck(0, pubKeysStrings);
    final String noIndices =
        String.format(
            "Skipping validators doppelgangers check for public keys %s. No associated indices found. Public keys are inactive",
            toAbbreviatedKeys(pubKeys).collect(Collectors.joining(", ")));
    logCaptor.assertInfoLog(noIndices);
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(new HashMap<>());
  }

  @Test
  public void shouldSkipKeysWithoutIndex() {
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    when(validatorApiChannel.getValidatorIndices(pubKeys))
        .thenReturn(
            SafeFuture.completedFuture(
                Map.ofEntries(Map.entry(pubKey1, 1), Map.entry(pubKey2, 2))));
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(1), dataStructureUtil.randomEpoch(), true),
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(2), dataStructureUtil.randomEpoch(), false)))));
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(120);
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog).doppelgangerCheck(0, pubKeysStrings);
    final String noIndex =
        String.format(
            "Skipping doppelganger check for public keys %s. No associated indices found. Public keys are inactive",
            pubKey3.toAbbreviatedString());
    logCaptor.assertInfoLog(noIndex);
    logCaptor.assertFatalLog(doppelgangerDetectedLog);
    Map<UInt64, BLSPublicKey> doppelgangers = Map.ofEntries(Map.entry(UInt64.valueOf(1), pubKey1));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            doppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(doppelgangers);
  }

  @Test
  public void shouldNotStartDoppelgangerDetectionWhenEmptyKeySet() {
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(new HashSet<>());
    logCaptor.assertInfoLog("Skipping doppelganger detection: No public keys provided to check");
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldNotCheckPreviousEpochAtFirstRunAtEpochZero() {
    when(genesisDataProvider.getGenesisTime())
        .thenReturn(SafeFuture.completedFuture(UInt64.valueOf(0)));
    timeProvider.advanceTimeBy(Duration.of(13, ChronoUnit.MINUTES));
    UInt64 currentSlot = spec.getCurrentSlot(timeProvider.getTimeInSeconds(), UInt64.ZERO);
    UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(new ArrayList<>())))
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
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(1);
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog, times(1)).doppelgangerCheck(currentEpoch.longValue(), pubKeysStrings);
    logCaptor.assertFatalLog(doppelgangerDetectedLog);
    Map<UInt64, BLSPublicKey> doppelgangers =
        Map.ofEntries(Map.entry(UInt64.valueOf(1), pubKey1), Map.entry(UInt64.valueOf(3), pubKey3));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            doppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(doppelgangers);
  }

  @Test
  public void shouldCheckPreviousAndCurrentEpochAtFirstRun() {
    when(genesisDataProvider.getGenesisTime())
        .thenReturn(SafeFuture.completedFuture(UInt64.valueOf(0)));
    timeProvider.advanceTimeBy(Duration.of(20, ChronoUnit.MINUTES));
    UInt64 currentSlot = spec.getCurrentSlot(timeProvider.getTimeInSeconds(), UInt64.ZERO);
    UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(new ArrayList<>())))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(1), dataStructureUtil.randomEpoch(), false),
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(2), dataStructureUtil.randomEpoch(), false),
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(3), dataStructureUtil.randomEpoch(), false)))));
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(new ArrayList<>())))
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
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(1);
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog, times(1))
        .doppelgangerCheck(currentEpoch.minus(1).longValue(), pubKeysStrings);
    verify(statusLog, times(1)).doppelgangerCheck(currentEpoch.longValue(), pubKeysStrings);
    logCaptor.assertFatalLog(doppelgangerDetectedLog);
    Map<UInt64, BLSPublicKey> doppelgangers =
        Map.ofEntries(Map.entry(UInt64.valueOf(1), pubKey1), Map.entry(UInt64.valueOf(3), pubKey3));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            doppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(doppelgangers);
  }

  private Stream<String> toAbbreviatedKeys(Set<BLSPublicKey> pubKeys) {
    return pubKeys.stream().map(BLSPublicKey::toAbbreviatedString);
  }
}

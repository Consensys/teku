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
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
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

  @Test
  public void shouldNotDetectDoppelganger() {
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(UInt64.ZERO));
    when(timeProvider.getTimeInMillis())
        .thenReturn(UInt64.valueOf(1000))
        .thenReturn(UInt64.valueOf(2000));
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
            Duration.ofMinutes(20));
    assertThat(doppelgangerDetectionService.start()).isCompleted();
    assertThat(logCaptor.getLogEvents()).isNotEmpty();
    assertThat(logCaptor.getLogEvents().get(0).getLevel()).isEqualTo("INFO");
    assertThat(logCaptor.getLogEvents().get(0).getMessage())
        .isEqualTo("No validator doppelganger detected. Stopping doppelganger detection service.");
  }

  @Test
  public void shouldStopAfterExceedingMaxAllowedDuration() {
    when(genesisDataProvider.getGenesisTime())
        .thenReturn(SafeFuture.failedFuture(new Exception("Dummy Exception")));
    when(timeProvider.getTimeInMillis())
        .thenReturn(UInt64.valueOf(1000))
        .thenReturn(UInt64.valueOf(2000));
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.completedFuture(IntArrayList.of(1, 2, 3)));
    when(validatorApiChannel.checkValidatorsDoppelganger(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(
                            UInt64.valueOf(3), dataStructureUtil.randomEpoch(), true),
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
            Duration.ofSeconds(10));
    assertThat(doppelgangerDetectionService.start()).isCompleted();
    assertThat(logCaptor.getLogEvents()).isNotEmpty();
    assertThat(logCaptor.getLogEvents().size()).isEqualTo(6);
    assertThat(logCaptor.getLogEvents().get(0).getLevel()).isEqualTo("ERROR");
    assertThat(logCaptor.getLogEvents().get(0).getMessage())
        .contains("Unable to check validators doppelganger: java.lang.Exception: Dummy Exception");
    assertThat(logCaptor.getLogEvents().get(1).getLevel()).isEqualTo("ERROR");
    assertThat(logCaptor.getLogEvents().get(1).getMessage())
        .contains("Unable to check validators doppelganger: java.lang.Exception: Dummy Exception");
    assertThat(logCaptor.getLogEvents().get(2).getLevel()).isEqualTo("ERROR");
    assertThat(logCaptor.getLogEvents().get(2).getMessage())
        .contains("Unable to check validators doppelganger: java.lang.Exception: Dummy Exception");
    assertThat(logCaptor.getLogEvents().get(3).getLevel()).isEqualTo("ERROR");
    assertThat(logCaptor.getLogEvents().get(3).getMessage())
        .contains("Unable to check validators doppelganger: java.lang.Exception: Dummy Exception");
    assertThat(logCaptor.getLogEvents().get(4).getLevel()).isEqualTo("INFO");
    assertThat(logCaptor.getLogEvents().get(4).getMessage())
        .contains("Doppelganger Detection Service max allowed duration exceeded.");
    assertThat(logCaptor.getLogEvents().get(5).getLevel()).isEqualTo("INFO");
    assertThat(logCaptor.getLogEvents().get(5).getMessage())
        .contains("No validator doppelganger detected. Stopping doppelganger detection service.");
  }
}

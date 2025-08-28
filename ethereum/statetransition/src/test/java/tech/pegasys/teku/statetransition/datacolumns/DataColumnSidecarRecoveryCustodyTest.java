/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.datacolumns.DasCustodyStand.createCustodyGroupCountManager;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.NoOpKZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

@SuppressWarnings("FutureReturnValueIgnored")
public class DataColumnSidecarRecoveryCustodyTest {

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(stubTimeProvider);

  private final SpecConfigFulu config =
      SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);

  private final DataColumnSidecarByRootCustody delegate =
      mock(DataColumnSidecarByRootCustody.class);
  private final MiscHelpersFulu miscHelpersFulu = mock(MiscHelpersFulu.class);
  private final DataColumnSidecarManager.ValidDataColumnSidecarsListener listener =
      mock(DataColumnSidecarManager.ValidDataColumnSidecarsListener.class);

  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();

  @SuppressWarnings("unchecked")
  private final Consumer<DataColumnSidecar> dataColumnSidecarPublisher = mock(Consumer.class);

  private final Supplier<Stream<UInt64>> columnIndices =
      () ->
          Stream.iterate(
              UInt64.ZERO,
              i -> i.isLessThan(UInt64.valueOf(config.getNumberOfColumns())),
              UInt64::increment);
  private final UInt64 slot = UInt64.ONE;
  private final BeaconBlockBody beaconBlockBody =
      dataStructureUtil.randomBeaconBlockBodyWithCommitments(1);
  private final BeaconBlock block = dataStructureUtil.randomBeaconBlock(slot, beaconBlockBody);
  private final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.signedBlock(block);

  private final DataColumnSidecarRecoveringCustody custody =
      new DataColumnSidecarRecoveringCustodyImpl(
          delegate,
          stubAsyncRunner,
          spec,
          miscHelpersFulu,
          NoOpKZG.INSTANCE,
          dataColumnSidecarPublisher,
          () ->
              createCustodyGroupCountManager(
                  config.getNumberOfCustodyGroups(), config.getSamplesPerSlot()),
          config.getNumberOfColumns(),
          config.getNumberOfCustodyGroups(),
          __ -> Duration.ofSeconds(2),
          stubMetricsSystem,
          stubTimeProvider);

  @BeforeEach
  public void setup() {
    custody.subscribeToValidDataColumnSidecars(listener);
    when(delegate.onNewValidatedDataColumnSidecar(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void shouldNotWorkUntilFulu() {
    final Spec spec = TestSpecFactory.createMinimalElectra();
    final DataColumnSidecarRecoveringCustody custody =
        new DataColumnSidecarRecoveringCustodyImpl(
            delegate,
            stubAsyncRunner,
            spec,
            miscHelpersFulu,
            NoOpKZG.INSTANCE,
            dataColumnSidecarPublisher,
            () -> createCustodyGroupCountManager(0, config.getSamplesPerSlot()),
            config.getNumberOfColumns(),
            config.getNumberOfCustodyGroups(),
            __ -> Duration.ofSeconds(2),
            stubMetricsSystem,
            stubTimeProvider);

    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  public void shouldNotWorkOnNonSupernode() {
    final DataColumnSidecarRecoveringCustody custody =
        new DataColumnSidecarRecoveringCustodyImpl(
            delegate,
            stubAsyncRunner,
            spec,
            miscHelpersFulu,
            NoOpKZG.INSTANCE,
            dataColumnSidecarPublisher,
            () -> CustodyGroupCountManager.NOOP,
            config.getNumberOfColumns(),
            config.getNumberOfCustodyGroups(),
            __ -> Duration.ofSeconds(2),
            stubMetricsSystem,
            stubTimeProvider);

    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  public void shouldWorkOnFuluSupernode() {
    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    custody.onNewBlock(signedBeaconBlock, Optional.empty());
    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars.values().stream().skip(30).limit(70).forEach(custody::onNewValidatedDataColumnSidecar);

    when(delegate.getCustodyDataColumnSidecar(any()))
        .thenAnswer(
            args -> {
              final DataColumnSlotAndIdentifier id = args.getArgument(0);
              return SafeFuture.completedFuture(
                  Optional.ofNullable(sidecars.get(id.columnIndex())));
            });
    when(miscHelpersFulu.reconstructAllDataColumnSidecars(anyCollection(), any()))
        .thenReturn(sidecars.values().stream().toList());
    stubAsyncRunner.executeQueuedActions();
    stubAsyncRunner.executeQueuedActions();

    columnIndices
        .get()
        .limit(30)
        .forEach(
            i -> {
              verify(delegate).onNewValidatedDataColumnSidecar(eq(sidecars.get(i)));
              verify(listener).onNewValidSidecar(eq(sidecars.get(i)), eq(RemoteOrigin.RECOVERED));
              verify(dataColumnSidecarPublisher).accept(eq(sidecars.get(i)));
            });
    columnIndices
        .get()
        .skip(100)
        .forEach(
            i -> {
              verify(delegate).onNewValidatedDataColumnSidecar(eq(sidecars.get(i)));
              verify(listener).onNewValidSidecar(eq(sidecars.get(i)), eq(RemoteOrigin.RECOVERED));
              verify(dataColumnSidecarPublisher).accept(eq(sidecars.get(i)));
            });
  }

  @Test
  public void shouldNotWorkOnFuluSupernodeForLocalProposal() {
    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    custody.onNewBlock(signedBeaconBlock, Optional.of(RemoteOrigin.LOCAL_PROPOSAL));
    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars.values().stream().skip(30).limit(70).forEach(custody::onNewValidatedDataColumnSidecar);
    stubAsyncRunner.executeQueuedActions();
    stubAsyncRunner.executeQueuedActions();

    verify(delegate, never()).getCustodyDataColumnSidecar(any());
    verifyNoInteractions(miscHelpersFulu);
  }

  @Test
  public void shouldNotWorkOnFuluSupernodeForLocalEL() {
    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    custody.onNewBlock(signedBeaconBlock, Optional.of(RemoteOrigin.LOCAL_EL));
    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars.values().stream().skip(30).limit(70).forEach(custody::onNewValidatedDataColumnSidecar);
    stubAsyncRunner.executeQueuedActions();
    stubAsyncRunner.executeQueuedActions();

    verify(delegate, never()).getCustodyDataColumnSidecar(any());
    verifyNoInteractions(miscHelpersFulu);
  }

  @Test
  public void shouldWaitUntilTimeouted() {
    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    custody.onNewBlock(signedBeaconBlock, Optional.empty());
    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars.values().stream().skip(30).limit(70).forEach(custody::onNewValidatedDataColumnSidecar);

    when(delegate.getCustodyDataColumnSidecar(any()))
        .thenAnswer(
            args -> {
              final DataColumnSlotAndIdentifier id = args.getArgument(0);
              return SafeFuture.completedFuture(
                  Optional.ofNullable(sidecars.get(id.columnIndex())));
            });
    when(miscHelpersFulu.reconstructAllDataColumnSidecars(anyCollection(), any()))
        .thenReturn(sidecars.values().stream().toList());
    stubAsyncRunner.executeDueActionsRepeatedly();
    stubTimeProvider.advanceTimeBySeconds(1);
    stubAsyncRunner.executeDueActionsRepeatedly();

    verify(delegate, never()).getCustodyDataColumnSidecar(any());

    stubTimeProvider.advanceTimeBySeconds(1);
    stubAsyncRunner.executeDueActionsRepeatedly();

    // prepare
    verify(delegate, times(70)).getCustodyDataColumnSidecar(any());

    // post reconstructed
    verify(delegate, times(config.getNumberOfColumns())).onNewValidatedDataColumnSidecar(any());
    verify(listener, times(58)).onNewValidSidecar(any(), eq(RemoteOrigin.RECOVERED));
    verify(dataColumnSidecarPublisher, times(58)).accept(any());
  }

  @Test
  public void shouldWaitUntilTimeoutedAndHalfOfSidecars() {
    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    custody.onNewBlock(signedBeaconBlock, Optional.empty());
    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars.values().stream().skip(30).limit(63).forEach(custody::onNewValidatedDataColumnSidecar);

    when(delegate.getCustodyDataColumnSidecar(any()))
        .thenAnswer(
            args -> {
              final DataColumnSlotAndIdentifier id = args.getArgument(0);
              return SafeFuture.completedFuture(
                  Optional.ofNullable(sidecars.get(id.columnIndex())));
            });
    when(miscHelpersFulu.reconstructAllDataColumnSidecars(anyCollection(), any()))
        .thenReturn(sidecars.values().stream().toList());
    stubAsyncRunner.executeDueActionsRepeatedly();
    stubTimeProvider.advanceTimeBySeconds(1);
    stubAsyncRunner.executeDueActionsRepeatedly();

    verify(delegate, never()).getCustodyDataColumnSidecar(any());

    stubTimeProvider.advanceTimeBySeconds(1);
    stubAsyncRunner.executeDueActionsRepeatedly();

    verify(delegate, never()).getCustodyDataColumnSidecar(any());

    custody.onNewValidatedDataColumnSidecar(sidecars.get(UInt64.ZERO));

    stubAsyncRunner.executeDueActionsRepeatedly();
    // prepare
    verify(delegate, times(64)).getCustodyDataColumnSidecar(any());

    // post reconstructed
    verify(delegate, times(config.getNumberOfColumns())).onNewValidatedDataColumnSidecar(any());
    verify(listener, times(64)).onNewValidSidecar(any(), eq(RemoteOrigin.RECOVERED));
    verify(dataColumnSidecarPublisher, times(64)).accept(any());
  }
}

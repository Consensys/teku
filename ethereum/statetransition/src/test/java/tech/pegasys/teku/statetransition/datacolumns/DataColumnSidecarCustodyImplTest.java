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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDB;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;

@SuppressWarnings({"FutureReturnValueIgnored"})
public class DataColumnSidecarCustodyImplTest {

  final Spec spec = TestSpecFactory.createMinimalFulu();
  final DataColumnSidecarDB db = new DataColumnSidecarDBStub();
  final DataColumnSidecarDbAccessor dbAccessor =
      DataColumnSidecarDbAccessor.builder(db).spec(spec).build();
  final CanonicalBlockResolverStub blockResolver = new CanonicalBlockResolverStub(spec);
  final CustodyGroupCountManager custodyGroupCountManager = mock(CustodyGroupCountManager.class);
  final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier =
      () -> custodyGroupCountManager;

  final SpecConfigFulu config =
      SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
  final int groupCount = config.getNumberOfCustodyGroups();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);
  private final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator =
      MinCustodyPeriodSlotCalculator.createFromSpec(spec);

  private DataColumnSidecarCustodyImpl custody;

  public static Stream<Arguments> minCustodyScenarios() {
    return Stream.of(
        Arguments.of(0, 0),
        Arguments.of(32768, 0),
        Arguments.of(32765, 0),
        Arguments.of(32776, 8),
        Arguments.of(32784, 16));
  }

  @BeforeEach
  public void setup() {
    custody =
        new DataColumnSidecarCustodyImpl(
            spec,
            blockResolver,
            dbAccessor,
            minCustodyPeriodSlotCalculator,
            custodyGroupCountManagerSupplier,
            groupCount);
    when(custodyGroupCountManager.getCustodyColumnIndices())
        .thenReturn(
            List.of(UInt64.valueOf(0), UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3)));
  }

  private void initWithMockDb(final int initialGroupCount, final int updatedGroupCount) {
    final DataColumnSidecarDbAccessor dbAccessorMock = mock(DataColumnSidecarDbAccessor.class);
    when(dbAccessorMock.setFirstCustodyIncompleteSlot(any())).thenReturn(SafeFuture.COMPLETE);
    custody =
        new DataColumnSidecarCustodyImpl(
            spec,
            blockResolver,
            dbAccessorMock,
            minCustodyPeriodSlotCalculator,
            custodyGroupCountManagerSupplier,
            initialGroupCount);
    when(custodyGroupCountManager.getCustodyGroupCount()).thenReturn(updatedGroupCount);
  }

  @Test
  void sanityTest() throws Throwable {
    BeaconBlock block = blockResolver.addBlock(10, true);
    DataColumnSidecar sidecar0 = createSidecar(block, 0);
    DataColumnSidecar sidecar1 = createSidecar(block, 1);
    DataColumnSlotAndIdentifier columnId0 = DataColumnSlotAndIdentifier.fromDataColumn(sidecar0);

    SafeFuture<Optional<DataColumnSidecar>> futureZero =
        custody.getCustodyDataColumnSidecar(columnId0);
    assertThat(futureZero.get(1, TimeUnit.SECONDS)).isEmpty();

    custody.onNewValidatedDataColumnSidecar(sidecar1, RemoteOrigin.GOSSIP);
    custody.onNewValidatedDataColumnSidecar(sidecar0, RemoteOrigin.GOSSIP);

    SafeFuture<Optional<DataColumnSidecar>> futureTwo =
        custody.getCustodyDataColumnSidecar(columnId0);
    SafeFuture<Optional<DataColumnSidecar>> futureThree =
        custody.getCustodyDataColumnSidecar(columnId0);

    assertThat(futureTwo.get().get()).isEqualTo(sidecar0);
    assertThat(futureThree.get().get()).isEqualTo(sidecar0);
  }

  @Test
  public void onSlot_ignoresNonEpochBoundary() {
    when(custodyGroupCountManager.getCustodyGroupCount()).thenReturn(groupCount + 3);
    custody.onSlot(UInt64.valueOf(1));
    assertThat(custody.getTotalCustodyGroupCount()).isEqualTo(groupCount);
    verifyNoInteractions(custodyGroupCountManager);
  }

  @Test
  public void onSlot_checksOnEpochBoundarySlot() {
    when(custodyGroupCountManager.getCustodyGroupCount()).thenReturn(groupCount + 3);
    custody.onSlot(UInt64.valueOf(8));
    assertThat(custody.getTotalCustodyGroupCount()).isEqualTo(groupCount + 3);
    verify(custodyGroupCountManager).getCustodyGroupCount();
  }

  @Test
  public void onNewFinalizedCheckpoint_noFinalizedColumnsAvailable() {
    final SafeFuture<Void> future = custody.advanceFirstIncompleteSlot(UInt64.valueOf(2));
    assertThat(future).isCompleted();
    // if epoch 2 is final, then slots 0-23 are 'final' and slot 24 is the first non final
    assertThat(dbAccessor.getFirstCustodyIncompleteSlot())
        .isCompletedWithValue(Optional.of(UInt64.valueOf(24)));
  }

  @ParameterizedTest
  @MethodSource("minCustodyScenarios")
  public void computesCustodyPeriodCorrectly(final int currentSlot, final int expectedCustodySlot) {
    assertThat(minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(UInt64.valueOf(currentSlot)))
        .isEqualTo(UInt64.valueOf(expectedCustodySlot));
  }

  @Test
  public void getBlockRootWithBlobs_emptySlot() {
    final CanonicalBlockResolver resolver = mock(CanonicalBlockResolver.class);
    final DataColumnSidecarDbAccessor sidecarDb = mock(DataColumnSidecarDbAccessor.class);
    when(resolver.getBlockAtSlot(ONE)).thenReturn(SafeFuture.completedFuture(Optional.empty()));
    custody =
        new DataColumnSidecarCustodyImpl(
            spec,
            resolver,
            sidecarDb,
            minCustodyPeriodSlotCalculator,
            custodyGroupCountManagerSupplier,
            groupCount);

    final SafeFuture<Optional<Bytes32>> futureResult = custody.getBlockRootWithBlobs(ONE);
    verifyNoMoreInteractions(sidecarDb);
    assertThat(futureResult).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getBlockRootWithBlobs_hasBlock() {
    final CanonicalBlockResolver resolver = mock(CanonicalBlockResolver.class);
    final DataColumnSidecarDbAccessor sidecarDb = mock(DataColumnSidecarDbAccessor.class);
    when(resolver.getBlockAtSlot(ONE))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomBeaconBlock(ONE))));
    custody =
        new DataColumnSidecarCustodyImpl(
            spec,
            resolver,
            sidecarDb,
            minCustodyPeriodSlotCalculator,
            custodyGroupCountManagerSupplier,
            groupCount);

    final SafeFuture<Optional<Bytes32>> futureResult = custody.getBlockRootWithBlobs(ONE);

    verifyNoMoreInteractions(sidecarDb);
    assertThat(futureResult)
        .isCompletedWithValue(
            Optional.of(
                Bytes32.fromHexString(
                    "0xae7c83ad2413e42c4de0ca0b59ef642cee15332ad401b8a3c800fd66d008a706")));
  }

  @Test
  public void retrieveSlotCustody_insufficientCustody()
      throws ExecutionException, InterruptedException, TimeoutException {
    final CanonicalBlockResolver resolver = mock(CanonicalBlockResolver.class);
    final DataColumnSidecarDbAccessor sidecarDb = mock(DataColumnSidecarDbAccessor.class);
    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(ONE);
    final DataColumnIdentifier dataColumnIdentifier =
        new DataColumnIdentifier(beaconBlock.getRoot(), UInt64.ZERO);
    when(resolver.getBlockAtSlot(ONE))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconBlock)));
    when(sidecarDb.getColumnIdentifiers(ONE))
        .thenReturn(
            SafeFuture.completedFuture(
                List.of(new DataColumnSlotAndIdentifier(ONE, dataColumnIdentifier))));
    when(custodyGroupCountManagerSupplier.get().getCustodyColumnIndices())
        .thenReturn(List.of(ZERO, ONE));
    custody =
        new DataColumnSidecarCustodyImpl(
            spec,
            resolver,
            sidecarDb,
            minCustodyPeriodSlotCalculator,
            custodyGroupCountManagerSupplier,
            2);

    SafeFuture<DataColumnSidecarCustodyImpl.SlotCustody> future = custody.retrieveSlotCustody(ONE);
    verify(sidecarDb).getColumnIdentifiers(ONE);
    verifyNoMoreInteractions(sidecarDb);
    final DataColumnSidecarCustodyImpl.SlotCustody slotCustody =
        future.get(100, TimeUnit.MILLISECONDS);

    assertThat(slotCustody.slot()).isEqualTo(ONE);
    assertThat(slotCustody.requiredColumnIndices()).isEqualTo(List.of(ZERO, ONE));
    assertThat(slotCustody.getIncompleteColumns())
        .containsExactly(
            new DataColumnSlotAndIdentifier(ONE, beaconBlock.getRoot(), UInt64.valueOf(1)));
  }

  @Test
  public void retrieveSlotCustody_shouldReturnEmptyIfOutsidePeriod() {
    custody.onSlot(UInt64.valueOf(100_000));

    final SafeFuture<DataColumnSidecarCustodyImpl.SlotCustody> future =
        custody.retrieveSlotCustody(ONE);

    assertThat(future)
        .isCompletedWithValue(
            new DataColumnSidecarCustodyImpl.SlotCustody(
                ONE, Optional.empty(), Collections.emptyList(), Collections.emptyList()));
  }

  @Test
  public void cgcDecreaseDoesntReduce() {
    initWithMockDb(128, 10);
    custody.onSlot(UInt64.valueOf(32));
    assertThat(custody.getTotalCustodyGroupCount()).isEqualTo(128);
  }

  @Test
  public void custodyIncreaseDoesIncreaseCgc() {
    initWithMockDb(10, 20);
    custody.onSlot(UInt64.valueOf(32));
    assertThat(custody.getTotalCustodyGroupCount()).isEqualTo(20);
  }

  private DataColumnSidecar createSidecar(final BeaconBlock block, final int column) {
    return dataStructureUtil.randomDataColumnSidecar(createSigned(block), UInt64.valueOf(column));
  }

  private SignedBeaconBlockHeader createSigned(final BeaconBlock block) {
    return dataStructureUtil.signedBlock(block).asHeader();
  }
}

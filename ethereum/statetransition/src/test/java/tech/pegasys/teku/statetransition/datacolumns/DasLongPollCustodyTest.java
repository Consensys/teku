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

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDB;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.db.DelayedDasDb;

@SuppressWarnings({"JavaCase", "FutureReturnValueIgnored"})
public class DasLongPollCustodyTest {
  final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(0);
  final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(stubTimeProvider);

  final Spec spec = TestSpecFactory.createMinimalEip7594();
  final DataColumnSidecarDB db = new DataColumnSidecarDBStub();
  final Duration dbDelay = ofMillis(5);
  final DelayedDasDb delayedDb = new DelayedDasDb(db, stubAsyncRunner, dbDelay);
  final DataColumnSidecarDbAccessor dbAccessor =
      DataColumnSidecarDbAccessor.builder(delayedDb).spec(spec).build();
  final CanonicalBlockResolverStub blockResolver = new CanonicalBlockResolverStub(spec);
  final UInt256 myNodeId = UInt256.ONE;

  final SpecConfigEip7594 config =
      SpecConfigEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).getConfig());
  final int subnetCount = config.getDataColumnSidecarSubnetCount();

  final DataColumnSidecarCustodyImpl custodyImpl =
      new DataColumnSidecarCustodyImpl(
          spec,
          blockResolver,
          dbAccessor,
          MinCustodyPeriodSlotCalculator.createFromSpec(spec),
          myNodeId,
          subnetCount);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);
  private final Duration currentSlotTimeout = ofSeconds(3);
  private final DasLongPollCustody custody =
      new DasLongPollCustody(custodyImpl, stubAsyncRunner, currentSlotTimeout);

  private final BeaconBlock block10 = blockResolver.addBlock(10, true);
  private final DataColumnSidecar sidecar10_0 = createSidecar(block10, 0);
  private final DataColumnSlotAndIdentifier columnId10_0 =
      DataColumnSlotAndIdentifier.fromDataColumn(sidecar10_0);
  private final DataColumnSidecar sidecar10_1 = createSidecar(block10, 1);
  private final DataColumnSlotAndIdentifier columnId10_1 =
      DataColumnSlotAndIdentifier.fromDataColumn(sidecar10_1);

  private DataColumnSidecar createSidecar(BeaconBlock block, int column) {
    return dataStructureUtil.randomDataColumnSidecar(createSigned(block), UInt64.valueOf(column));
  }

  private SignedBeaconBlockHeader createSigned(BeaconBlock block) {
    return dataStructureUtil.signedBlock(block).asHeader();
  }

  private void advanceTimeGradually(Duration delta) {
    for (int i = 0; i < delta.toMillis(); i++) {
      stubTimeProvider.advanceTimeBy(ofMillis(1));
      stubAsyncRunner.executeDueActionsRepeatedly();
    }
  }

  @Test
  void testLongPollingColumnRequest() throws Exception {
    SafeFuture<Optional<DataColumnSidecar>> fRet0 =
        custody.getCustodyDataColumnSidecar(columnId10_0);
    SafeFuture<Optional<DataColumnSidecar>> fRet0_1 =
        custody.getCustodyDataColumnSidecar(columnId10_0);
    SafeFuture<Optional<DataColumnSidecar>> fRet1 =
        custody.getCustodyDataColumnSidecar(columnId10_1);

    advanceTimeGradually(currentSlotTimeout.minus(dbDelay).minus(ofMillis(1)));

    custody.onSlot(UInt64.valueOf(10));

    assertThat(fRet0).isNotDone();
    assertThat(fRet0_1).isNotDone();
    assertThat(fRet1).isNotDone();

    custody.onNewValidatedDataColumnSidecar(sidecar10_0);

    advanceTimeGradually(dbDelay);

    assertThat(fRet0).isCompletedWithValue(Optional.of(sidecar10_0));
    assertThat(fRet0_1).isCompletedWithValue(Optional.of(sidecar10_0));
    assertThat(fRet1).isNotDone();

    advanceTimeGradually(currentSlotTimeout);

    assertThat(fRet1).isCompletedWithValue(Optional.empty());
  }

  @Test
  void testPendingRequestIsExecutedWhenLongReadQuickWrite() throws Exception {
    // long DB read
    delayedDb.setDelay(ofMillis(10));
    SafeFuture<Optional<DataColumnSidecar>> fRet0 =
        custody.getCustodyDataColumnSidecar(columnId10_0);
    advanceTimeGradually(ofMillis(1));

    // quicker DB write
    delayedDb.setDelay(ofMillis(5));
    custody.onNewValidatedDataColumnSidecar(sidecar10_0);

    advanceTimeGradually(ofMillis(10));
    assertThat(fRet0).isCompletedWithValue(Optional.ofNullable(sidecar10_0));
  }

  @Test
  void testPendingRequestIsExecutedWhenLongWriteQuickRead() throws Exception {
    // long DB write
    delayedDb.setDelay(ofMillis(10));
    custody.onNewValidatedDataColumnSidecar(sidecar10_0);

    advanceTimeGradually(ofMillis(1));
    // quicker DB read
    delayedDb.setDelay(ofMillis(5));
    SafeFuture<Optional<DataColumnSidecar>> fRet0 =
        custody.getCustodyDataColumnSidecar(columnId10_0);

    advanceTimeGradually(ofMillis(50));
    assertThat(fRet0).isCompletedWithValue(Optional.ofNullable(sidecar10_0));
  }

  @Test
  void testOptionalEmptyIsReturnedOnTimeout() {
    SafeFuture<Optional<DataColumnSidecar>> fRet0 =
        custody.getCustodyDataColumnSidecar(columnId10_0);

    custody.onSlot(UInt64.valueOf(9));
    advanceTimeGradually(currentSlotTimeout.plusMillis(100));

    assertThat(fRet0).isNotDone();

    custody.onSlot(UInt64.valueOf(10));

    advanceTimeGradually(currentSlotTimeout.minusMillis(10));
    assertThat(fRet0).isNotDone();

    advanceTimeGradually(Duration.ofMillis(110));
    assertThat(fRet0).isCompletedWithValue(Optional.empty());
  }

  @Test
  void testOptionalEmptyIsReturnedImmediatelyForPastSlot() {
    custody.onSlot(UInt64.valueOf(10));
    advanceTimeGradually(currentSlotTimeout);

    SafeFuture<Optional<DataColumnSidecar>> fRet =
        custody.getCustodyDataColumnSidecar(columnId10_0);
    advanceTimeGradually(Duration.ofMillis(20)); // more than db delay
    assertThat(fRet).isCompletedWithValue(Optional.empty());
  }
}

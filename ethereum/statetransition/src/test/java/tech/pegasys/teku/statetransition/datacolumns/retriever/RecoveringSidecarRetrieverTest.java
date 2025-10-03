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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetupLoader;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockResolverStub;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarDBStub;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDB;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;

@SuppressWarnings({"JavaCase"})
public class RecoveringSidecarRetrieverTest {

  static final Duration RECOVERY_INITIATION_TIMEOUT = Duration.ofSeconds(10);
  static final Duration RECOVERY_INITIATION_CHECK_INTERVAL = Duration.ofSeconds(1);

  final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInMillis(0);
  final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(stubTimeProvider);
  final Spec spec = TestSpecFactory.createMinimalFulu();
  final DataColumnSidecarDB db = new DataColumnSidecarDBStub();
  final DataColumnSidecarDbAccessor dbAccessor =
      DataColumnSidecarDbAccessor.builder(db).spec(spec).build();
  final CanonicalBlockResolverStub blockResolver = new CanonicalBlockResolverStub(spec);

  final SpecConfigFulu config =
      SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
  final MiscHelpersFulu miscHelpers =
      MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
  final int columnCount = config.getNumberOfColumns();
  final KZG kzg = KZG.getInstance(false);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);

  private DataColumnSidecarRetrieverStub delegateRetriever;
  private RecoveringSidecarRetriever recoverRetriever;

  public RecoveringSidecarRetrieverTest() {
    TrustedSetupLoader.loadTrustedSetupForTests(kzg);
  }

  private SignedBeaconBlock createSigned(final BeaconBlock block) {
    return dataStructureUtil.signedBlock(block);
  }

  private DataColumnSlotAndIdentifier createId(final BeaconBlock block, final int colIdx) {
    return new DataColumnSlotAndIdentifier(
        block.getSlot(), block.getRoot(), UInt64.valueOf(colIdx));
  }

  @BeforeEach
  void setUp() {
    delegateRetriever = new DataColumnSidecarRetrieverStub();
    recoverRetriever =
        new RecoveringSidecarRetriever(
            delegateRetriever,
            miscHelpers,
            blockResolver,
            dbAccessor,
            stubAsyncRunner,
            RECOVERY_INITIATION_TIMEOUT,
            RECOVERY_INITIATION_CHECK_INTERVAL,
            stubTimeProvider,
            128);

    recoverRetriever.start();
  }

  @Test
  @SuppressWarnings("deprecation")
  void sanityTest() {
    int blobCount = 3;
    int columnsInDbCount = 3;

    List<Blob> blobs =
        Stream.generate(dataStructureUtil::randomValidBlob).limit(blobCount).toList();
    BeaconBlock block = blockResolver.addBlock(10, blobCount);
    List<DataColumnSidecar> sidecars =
        miscHelpers.constructDataColumnSidecarsOld(createSigned(block), blobs);

    List<Integer> dbColumnIndices =
        IntStream.range(10, Integer.MAX_VALUE).limit(columnsInDbCount).boxed().toList();
    dbColumnIndices.forEach(idx -> assertThat(db.addSidecar(sidecars.get(idx))).isDone());

    DataColumnSlotAndIdentifier id0 = createId(block, 0);
    DataColumnSlotAndIdentifier id1 = createId(block, 1);
    DataColumnSlotAndIdentifier id2 = createId(block, 2);
    SafeFuture<DataColumnSidecar> res0 = recoverRetriever.retrieve(id0);
    SafeFuture<DataColumnSidecar> res1 = recoverRetriever.retrieve(id1);
    SafeFuture<DataColumnSidecar> res2 = recoverRetriever.retrieve(id2);

    assertThat(delegateRetriever.requests).hasSize(3);
    assertThat(recoverRetriever.pendingRequestsCount()).isEqualTo(3);

    // id2 future completes immediately
    delegateRetriever.requests.get(2).future().complete(sidecars.get(2));
    assertThat(res2).isCompletedWithValue(sidecars.get(2));
    assertThat(recoverRetriever.pendingRequestsCount()).isEqualTo(2);

    stubAsyncRunner.executeDueActions();
    // does nothing
    assertThat(delegateRetriever.requests).hasSize(3);

    // 3 pending promises
    assertThat(recoverRetriever.pendingRequestsCount()).isEqualTo(2);

    // advance to the first check
    stubTimeProvider.advanceTimeBy(RECOVERY_INITIATION_CHECK_INTERVAL);
    stubAsyncRunner.executeDueActions();

    // 2 pending promises, one is already completed
    assertThat(recoverRetriever.pendingRequestsCount()).isEqualTo(2);

    // should initiate recovery
    stubTimeProvider.advanceTimeBy(RECOVERY_INITIATION_TIMEOUT);
    stubAsyncRunner.executeDueActions();

    assertThat(delegateRetriever.requests).hasSize(3 + columnCount - columnsInDbCount);

    assertThat(delegateRetriever.requests).hasSize(3 + columnCount - columnsInDbCount);

    // no more pending promises
    assertThat(recoverRetriever.pendingRequestsCount()).isZero();

    delegateRetriever.requests.stream()
        .skip(50)
        .limit(columnCount / 2 - columnsInDbCount)
        .forEach(
            req -> req.future().complete(sidecars.get(req.columnId().columnIndex().intValue())));

    stubAsyncRunner.executeDueActionsRepeatedly();

    assertThat(res0).isCompletedWithValue(sidecars.get(0));
    assertThat(res1).isCompletedWithValue(sidecars.get(1));

    assertThat(delegateRetriever.requests).allMatch(r -> r.future().isDone());
  }

  @Test
  @SuppressWarnings("deprecation")
  void succeededAndFailedRetrievalShouldBeImmediatelyRemovedFromPendingPromises() {
    int blobCount = 3;
    int columnsInDbCount = 3;

    List<Blob> blobs =
        Stream.generate(dataStructureUtil::randomValidBlob).limit(blobCount).toList();
    BeaconBlock block = blockResolver.addBlock(10, blobCount);
    List<DataColumnSidecar> sidecars =
        miscHelpers.constructDataColumnSidecarsOld(createSigned(block), blobs);

    List<Integer> dbColumnIndices =
        IntStream.range(10, Integer.MAX_VALUE).limit(columnsInDbCount).boxed().toList();
    dbColumnIndices.forEach(idx -> assertThat(db.addSidecar(sidecars.get(idx))).isDone());

    DataColumnSlotAndIdentifier id0 = createId(block, 0);
    DataColumnSlotAndIdentifier id1 = createId(block, 1);
    DataColumnSlotAndIdentifier id2 = createId(block, 2);
    SafeFuture<DataColumnSidecar> res0 = recoverRetriever.retrieve(id0);
    SafeFuture<DataColumnSidecar> res1 = recoverRetriever.retrieve(id1);
    SafeFuture<DataColumnSidecar> res2 = recoverRetriever.retrieve(id2);

    assertThat(delegateRetriever.requests).hasSize(3);
    assertThat(recoverRetriever.pendingRequestsCount()).isEqualTo(3);

    res0.complete(sidecars.get(0));
    res1.completeExceptionally(new RuntimeException("error"));

    assertThat(recoverRetriever.pendingRequestsCount()).isEqualTo(1);
    assertThatSafeFuture(res2).isNotCompleted();
  }

  @Test
  @SuppressWarnings("deprecation")
  void testMoreThanOneBlockWithBlobsOnSameSlot() {
    int blobCount = 1;
    int columnsInDbCount = 13;

    List<Blob> blobs_10_0 =
        Stream.generate(dataStructureUtil::randomValidBlob).limit(blobCount).toList();
    BeaconBlock block_10_0 = blockResolver.addBlock(10, blobCount);
    List<DataColumnSidecar> sidecars_10_0 =
        miscHelpers.constructDataColumnSidecarsOld(createSigned(block_10_0), blobs_10_0);
    sidecars_10_0.forEach(sidecar -> assertThat(db.addSidecar(sidecar)).isDone());

    List<Blob> blobs_10_1 =
        Stream.generate(dataStructureUtil::randomValidBlob).limit(blobCount).toList();
    BeaconBlock block_10_1 = blockResolver.addBlock(10, blobCount);
    List<DataColumnSidecar> sidecars_10_1 =
        miscHelpers.constructDataColumnSidecarsOld(createSigned(block_10_1), blobs_10_1);
    sidecars_10_1.stream()
        .limit(columnsInDbCount)
        .forEach(sidecar -> assertThat(db.addSidecar(sidecar)).isDone());

    DataColumnSlotAndIdentifier id0 = createId(block_10_1, 100);
    SafeFuture<DataColumnSidecar> res0 = recoverRetriever.retrieve(id0);

    assertThat(delegateRetriever.requests).hasSize(1);

    // should initiate recovery
    stubTimeProvider.advanceTimeBy(RECOVERY_INITIATION_TIMEOUT);
    stubAsyncRunner.executeDueActions();

    assertThat(delegateRetriever.requests).hasSize(1 + columnCount - columnsInDbCount);

    delegateRetriever.requests.stream()
        .skip(50)
        .limit(columnCount / 2 - columnsInDbCount)
        .forEach(
            req ->
                req.future().complete(sidecars_10_1.get(req.columnId().columnIndex().intValue())));

    stubAsyncRunner.executeDueActionsRepeatedly();

    assertThat(res0).isCompletedWithValue(sidecars_10_1.get(100));
    assertThat(delegateRetriever.requests).allMatch(r -> r.future().isDone());
  }

  @Test
  @SuppressWarnings("deprecation")
  void cancellingRequestShouldStopRecovery() {
    int blobCount = 3;
    int columnsInDbCount = 3;

    List<Blob> blobs =
        Stream.generate(dataStructureUtil::randomValidBlob).limit(blobCount).toList();
    BeaconBlock block = blockResolver.addBlock(10, blobCount);
    List<DataColumnSidecar> sidecars =
        miscHelpers.constructDataColumnSidecarsOld(createSigned(block), blobs);

    List<Integer> dbColumnIndices =
        IntStream.range(10, Integer.MAX_VALUE).limit(columnsInDbCount).boxed().toList();
    dbColumnIndices.forEach(idx -> assertThat(db.addSidecar(sidecars.get(idx))).isDone());

    DataColumnSlotAndIdentifier id0 = createId(block, 0);
    SafeFuture<DataColumnSidecar> res0 = recoverRetriever.retrieve(id0);

    assertThat(delegateRetriever.requests).hasSize(1);

    // should initiate recovery
    stubTimeProvider.advanceTimeBy(RECOVERY_INITIATION_TIMEOUT);
    stubAsyncRunner.executeDueActions();

    assertThat(delegateRetriever.requests).hasSize(1 + columnCount - columnsInDbCount);

    res0.cancel(true);

    stubAsyncRunner.executeQueuedActions();

    assertThat(delegateRetriever.requests).allMatch(r -> r.future().isCancelled());
  }
}

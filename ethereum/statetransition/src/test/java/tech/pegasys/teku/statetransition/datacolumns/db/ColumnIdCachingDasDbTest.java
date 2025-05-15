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

package tech.pegasys.teku.statetransition.datacolumns.db;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarDBStub;
import tech.pegasys.teku.statetransition.datacolumns.util.StubAsync;

@SuppressWarnings("FutureReturnValueIgnored")
public class ColumnIdCachingDasDbTest {
  final Spec spec = TestSpecFactory.createMinimalFulu();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);

  final Duration dbDelay = ofMillis(5);
  final StubAsync stubAsync = new StubAsync();

  DataColumnSidecarDBStub db = new DataColumnSidecarDBStub();
  DataColumnSidecarDB asyncDb = new DelayedDasDb(this.db, stubAsync.getStubAsyncRunner(), dbDelay);

  final int cacheSize = 2;
  ColumnIdCachingDasDb columnIdCachingDb = new ColumnIdCachingDasDb(asyncDb, __ -> 128, cacheSize);

  private DataColumnSidecar createSidecar(final int slot, final int index) {
    final UInt64 slotU = UInt64.valueOf(slot);
    final BeaconBlockBody beaconBlockBody =
        dataStructureUtil.randomBeaconBlockBodyWithCommitments(1);
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(slotU, beaconBlockBody);
    final SignedBeaconBlock signedBlock = dataStructureUtil.signedBlock(block);
    return dataStructureUtil.randomDataColumnSidecar(signedBlock.asHeader(), UInt64.valueOf(index));
  }

  @Test
  void sanityTest() {
    SafeFuture<List<DataColumnSlotAndIdentifier>> res1 =
        columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777));

    assertThat(res1).isNotDone();
    stubAsync.advanceTimeGradually(dbDelay);
    assertThat(res1).isCompletedWithValue(emptyList());
    long reads0 = db.getDbReadCounter().get();
    assertThat(reads0).isGreaterThan(0);

    SafeFuture<List<DataColumnSlotAndIdentifier>> res2 =
        columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777));

    assertThat(res2).isCompletedWithValue(emptyList());
    long reads1 = db.getDbReadCounter().get();
    assertThat(reads1).isEqualTo(reads0);
  }

  @Test
  void checkCacheIsInvalidated() {
    SafeFuture<List<DataColumnSlotAndIdentifier>> res1 =
        columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777));

    stubAsync.advanceTimeGradually(ofMillis(1));

    SafeFuture<Void> addCompleteFuture = columnIdCachingDb.addSidecar(createSidecar(777, 77));
    stubAsync.advanceTimeGraduallyUntilAllDone(ofSeconds(1));

    assertThat(res1)
        .isCompleted(); // no assumptions on result: cache may or may not pick up latest changes
    assertThat(addCompleteFuture).isCompleted();
    long reads0 = db.getDbReadCounter().get();
    assertThat(reads0).isGreaterThan(0);

    SafeFuture<List<DataColumnSlotAndIdentifier>> res2 =
        columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777));
    stubAsync.advanceTimeGradually(dbDelay);

    assertThat(res2).isCompletedWithValueMatching(l -> !l.isEmpty());
  }

  @Test
  void checkCacheIsPruned() {
    for (int i = 0; i < cacheSize + 1; i++) {
      columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777 + i));
    }
    stubAsync.advanceTimeGradually(dbDelay);
    long reads0 = db.getDbReadCounter().get();
    assertThat(reads0).isGreaterThan(0);

    columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777));
    stubAsync.advanceTimeGradually(dbDelay);
    long reads1 = db.getDbReadCounter().get();
    // the first cache entry (for slot 777) should be evicted and a query to underlying db should be
    // done
    assertThat(reads1).isGreaterThan(reads0);
  }
}

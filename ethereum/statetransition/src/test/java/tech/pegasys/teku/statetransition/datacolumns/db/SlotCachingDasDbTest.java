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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.DasCustodyStand;

@SuppressWarnings({"JavaCase", "FutureReturnValueIgnored"})
public class SlotCachingDasDbTest {
  final Spec spec = TestSpecFactory.createMinimalEip7594();
  final DasCustodyStand das = DasCustodyStand.builder(spec).build();
  final DataColumnSidecarDB db = Mockito.mock(DataColumnSidecarDB.class);
  SlotCachingDasDb slotCachingDasDb = new SlotCachingDasDb(db);

  @Test
  void checkSlotIsCached() {
    SignedBeaconBlock block10 = das.createBlockWithBlobs(10);
    SignedBeaconBlock block11 = das.createBlockWithBlobs(11);
    DataColumnSidecar sidecar10_0 = das.createSidecar(block10, 0);
    DataColumnSidecar sidecar10_1 = das.createSidecar(block10, 1);
    DataColumnSidecar sidecar11_0 = das.createSidecar(block11, 0);
    slotCachingDasDb.addSidecar(sidecar10_0);

    when(db.getSidecar(any(DataColumnSlotAndIdentifier.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(sidecar10_0)));
    when(db.getSidecar(any(DataColumnIdentifier.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(sidecar10_0)));

    slotCachingDasDb.getSidecar(DataColumnIdentifier.createFromSidecar(sidecar10_0));

    verify(db).getSidecar(any(DataColumnSlotAndIdentifier.class));
    verify(db, never()).getSidecar(any(DataColumnIdentifier.class));

    slotCachingDasDb.getSidecar(DataColumnIdentifier.createFromSidecar(sidecar10_1));

    // the blockRoot <-> slot should be cached
    verify(db, times(2)).getSidecar(any(DataColumnSlotAndIdentifier.class));
    verify(db, never()).getSidecar(any(DataColumnIdentifier.class));

    slotCachingDasDb.getSidecar(DataColumnIdentifier.createFromSidecar(sidecar11_0));

    // should fall back to query by DataColumnIdentifier when slot is not cached
    verify(db, times(2)).getSidecar(any(DataColumnSlotAndIdentifier.class));
    verify(db).getSidecar(any(DataColumnIdentifier.class));
  }

  @Test
  void checkCacheIsPruned() {
    SignedBeaconBlock block10 = das.createBlockWithBlobs(10);
    SignedBeaconBlock block11 = das.createBlockWithBlobs(11);
    DataColumnSidecar sidecar10_0 = das.createSidecar(block10, 0);
    DataColumnSidecar sidecar11_0 = das.createSidecar(block11, 0);

    slotCachingDasDb.addSidecar(sidecar10_0);
    slotCachingDasDb.addSidecar(sidecar11_0);

    when(db.getSidecar(any(DataColumnSlotAndIdentifier.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(sidecar10_0)));
    when(db.getSidecar(any(DataColumnIdentifier.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(sidecar10_0)));

    slotCachingDasDb.setFirstCustodyIncompleteSlot(UInt64.valueOf(11));

    slotCachingDasDb.getSidecar(DataColumnIdentifier.createFromSidecar(sidecar10_0));

    // slot 10 cache should be evicted
    verify(db, never()).getSidecar(any(DataColumnSlotAndIdentifier.class));
    verify(db).getSidecar(any(DataColumnIdentifier.class));

    slotCachingDasDb.getSidecar(DataColumnIdentifier.createFromSidecar(sidecar11_0));

    // slot 11 cache should not be evicted
    verify(db).getSidecar(any(DataColumnSlotAndIdentifier.class));
    verify(db).getSidecar(any(DataColumnIdentifier.class));
  }
}

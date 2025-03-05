/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.coordinator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class StoredLatestCanonicalBlockUpdaterTest {
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final StoredLatestCanonicalBlockUpdater updater =
      new StoredLatestCanonicalBlockUpdater(recentChainData);

  @Test
  void onSlot_shouldUpdateLatestCanonicalBlockRoot() {
    final Optional<Bytes32> blockRoot = Optional.of(Bytes32.fromHexString("0x01"));
    final StoreTransaction storeTransaction = mock(StoreTransaction.class);
    when(storeTransaction.commit()).thenReturn(SafeFuture.COMPLETE);

    when(recentChainData.startStoreTransaction()).thenReturn(storeTransaction);
    when(recentChainData.getBestBlockRoot()).thenReturn(blockRoot);

    updater.onSlot(UInt64.valueOf(33));

    verify(storeTransaction).setLatestCanonicalBlockRoot(blockRoot.get());
  }

  @Test
  void onSlot_shouldNotUpdateLatestCanonicalBlockRoot() {
    updater.onSlot(UInt64.valueOf(32));

    verifyNoInteractions(recentChainData);
  }
}

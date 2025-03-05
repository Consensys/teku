package tech.pegasys.teku.validator.coordinator;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class StoredLatestCanonicalBlockUpdaterTest {
    private final RecentChainData recentChainData = mock(RecentChainData.class);

    private final StoredLatestCanonicalBlockUpdater updater = new StoredLatestCanonicalBlockUpdater(recentChainData);

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

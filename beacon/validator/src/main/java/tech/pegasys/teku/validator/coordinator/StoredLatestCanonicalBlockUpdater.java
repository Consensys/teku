package tech.pegasys.teku.validator.coordinator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;


public class StoredLatestCanonicalBlockUpdater implements SlotEventsChannel {
    private static final Logger LOG = LogManager.getLogger();

    private final RecentChainData recentChainData;

    public StoredLatestCanonicalBlockUpdater(final RecentChainData recentChainData) {
        this.recentChainData = recentChainData;
    }

    @Override
    public void onSlot(UInt64 slot) {
        if(!slot.mod(UInt64.valueOf(32)).equals(ONE)) {
            return;
        }
        final StoreTransaction transaction = recentChainData.startStoreTransaction();
        recentChainData.getBestBlockRoot().ifPresent(transaction::setLatestCanonicalBlockRoot);
        transaction.commit().finish(error -> LOG.error("Failed to store latest canonical block root", error));
    }
}

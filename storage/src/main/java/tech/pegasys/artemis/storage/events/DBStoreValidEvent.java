package tech.pegasys.artemis.storage.events;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.storage.Store;

public class DBStoreValidEvent {
  private final Store store;
  private final UnsignedLong nodeSlot;

  public DBStoreValidEvent(final Store store, final UnsignedLong nodeSlot) {
    this.store = store;
    this.nodeSlot = nodeSlot;
  }

  public Store getStore() {
    return store;
  }

  public UnsignedLong getNodeSlot() {
    return nodeSlot;
  }
}

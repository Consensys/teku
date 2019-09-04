package tech.pegasys.artemis.storage.events;

import tech.pegasys.artemis.storage.Store;

public class DBStoreValidEvent {
  private final Store store;

  public DBStoreValidEvent(final Store store) {
    this.store = store;
  }

  public Store getStore() {
    return store;
  }
}

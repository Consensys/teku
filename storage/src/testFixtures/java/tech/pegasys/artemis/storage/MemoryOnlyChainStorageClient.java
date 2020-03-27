package tech.pegasys.artemis.storage;

import com.google.common.eventbus.EventBus;
import tech.pegasys.artemis.storage.api.StorageUpdateChannel;

public class MemoryOnlyChainStorageClient extends ChainStorageClient {

  public MemoryOnlyChainStorageClient(
          final EventBus eventBus,
          final StorageUpdateChannel) {
    super(new StubStorageUpdateChannel(), eventBus);
  }

  public static ChainStorageClient create(
          final EventBus eventBus) {
    MemoryOnlyChainStorageClient client = new MemoryOnlyChainStorageClient(eventBus);
    eventBus.register(client);
    return client;
  }

  public static ChainStorageClient createWithStore(
          final EventBus eventBus,
          final Store store) {
    MemoryOnlyChainStorageClient client = new MemoryOnlyChainStorageClient(eventBus);
    eventBus.register(client);
    client.setStore(store);
    return client;
  }
}

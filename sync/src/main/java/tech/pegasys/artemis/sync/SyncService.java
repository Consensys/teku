package tech.pegasys.artemis.sync;

import tech.pegasys.artemis.util.async.SafeFuture;

public interface SyncService {

  SafeFuture<?> start();

  SafeFuture<?> stop();

  SyncingStatus getSyncStatus();

  boolean isSyncActive();
}

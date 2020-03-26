package tech.pegasys.artemis.storage.api;

import tech.pegasys.artemis.storage.events.diskupdates.DiskUpdate;
import tech.pegasys.artemis.storage.events.diskupdates.DiskGenesisUpdate;
import tech.pegasys.artemis.storage.events.diskupdates.DiskUpdateResult;
import tech.pegasys.artemis.util.async.SafeFuture;

public interface DiskUpdateChannel {

  SafeFuture<DiskUpdateResult> onDiskUpdate(DiskUpdate event);

  void onDiskGenesisUpdate(DiskGenesisUpdate event);
}

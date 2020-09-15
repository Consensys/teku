package tech.pegasys.teku.sync.multipeer.batches;

import java.util.Optional;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;

public interface SyncSourceSelector {
  Optional<SyncSource> selectSource();
}

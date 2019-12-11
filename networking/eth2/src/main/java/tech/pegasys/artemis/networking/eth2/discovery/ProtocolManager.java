package tech.pegasys.artemis.networking.eth2.discovery;

import java.util.concurrent.CompletableFuture;

public interface ProtocolManager {
  enum State {
    RUNNING,
    STOPPED
  }

  /** Starts manager **/
  CompletableFuture<?> start();

  /** Stops manager **/
  CompletableFuture<?> stop();
}

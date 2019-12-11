package tech.pegasys.artemis.networking.eth2.discovery;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class DiscoveryManager implements ProtocolManager {

  private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);

  /**
   * Start discovery from idle or stopped state
   * @return Future indicating failure or State.RUNNING
   */
  @Override
  public CompletableFuture<?> start() {
    if (!state.compareAndSet(State.STOPPED, State.RUNNING)) {
      return CompletableFuture.failedFuture(new IllegalStateException("Network already started"));
    }
    return CompletableFuture.completedFuture(State.RUNNING);
  }

  /**
   * Stop discovery
   * @return Future indicating failure or State.STOPPED
   */
  @Override
  public CompletableFuture<?> stop() {
    if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return CompletableFuture.failedFuture(new IllegalStateException("Network already stopped"));
    }
    return CompletableFuture.completedFuture(State.STOPPED);
  }

  public State getState() {
    return state.get();
  }
}

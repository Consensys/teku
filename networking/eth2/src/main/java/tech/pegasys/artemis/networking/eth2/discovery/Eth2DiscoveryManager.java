package tech.pegasys.artemis.networking.eth2.discovery;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.ethereum.beacon.discovery.DiscoveryManager;

@SuppressWarnings("UnstableApiUsage")
public class Eth2DiscoveryManager implements ProtocolManager, EventBusHandler{

  private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);

  DiscoveryManager dm;

  private Optional<EventBus> eventBus = Optional.empty();

  static class DiscoveryRequest {

    public DiscoveryRequest(int numPeersToFind) {
      this.numPeersToFind = numPeersToFind;
    }

    int numPeersToFind;

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof DiscoveryRequest) {
        return ((DiscoveryRequest) obj).numPeersToFind == numPeersToFind;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return numPeersToFind;
    }
  }

  public Eth2DiscoveryManager() { }

  public Eth2DiscoveryManager(final EventBus eventBus) {
    this.eventBus = Optional.of(eventBus);
  }

  /**
   * Start discovery from idle or stopped state
   * @return Future indicating failure or State.RUNNING
   */
  @Override
  public CompletableFuture<?> start() {
    if (!state.compareAndSet(State.STOPPED, State.RUNNING)) {
      return CompletableFuture.failedFuture(new IllegalStateException("Network already started"));
    }
    eventBus.ifPresent(v -> { v.register(this);});
    return CompletableFuture.completedFuture(State.RUNNING);
  }

  @Subscribe
  public void onDiscoveryRequest(final DiscoveryRequest request) {
    if (request.numPeersToFind == 0) {
      this.stop();
      return;
    }
    this.start();
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
    eventBus.ifPresent(v -> { v.unregister(this); });
    return CompletableFuture.completedFuture(State.STOPPED);
  }

  public State getState() {
    return state.get();
  }

  @Override
  public void setEventBus(EventBus eventBus) {
    this.eventBus = Optional.of(eventBus);
    this.eventBus.ifPresent(eb -> { eb.register(this); });
  }

  @Override
  public Optional<EventBus> getEventBus() {
    return eventBus;
  }

}

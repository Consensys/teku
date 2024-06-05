package tech.pegasys.teku.infrastructure.events;

public interface EventChannelSubscriber<T extends ChannelInterface> {

  void subscribe(T listener);
}

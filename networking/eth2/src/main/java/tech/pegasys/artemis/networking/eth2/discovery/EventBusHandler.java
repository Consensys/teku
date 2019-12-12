package tech.pegasys.artemis.networking.eth2.discovery;

import com.google.common.eventbus.EventBus;
import java.util.Optional;

@SuppressWarnings("UnstableApiUsage")
public interface EventBusHandler {
    Optional<EventBus> getEventBus();
    void setEventBus(EventBus eventBus);
}

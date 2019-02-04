package tech.pegasys.artemis.services.adapter.io;

import java.io.IOException;
import java.util.Optional;
import tech.pegasys.artemis.pow.api.PowEvent;
import tech.pegasys.artemis.services.adapter.io.client.EventForwardingClient;
import tech.pegasys.artemis.services.adapter.event.EventDescriptor;
import tech.pegasys.artemis.services.adapter.event.OutboundEvent;

public interface ServiceAdapterIO {

  void startServer(int serverPort) throws IOException;

  void shutdownServer();

  void registerInboundEventEndpoint(EventDescriptor eventDescriptor, InboundEventHandler handler);

  void registerOutboundEventClient(OutboundEvent outboundEvent);

  Optional<EventForwardingClient> getOutboundEventClient(String eventType);

  interface InboundEventHandler {
    void onInboundEvent(PowEvent<?> event);
  }
}

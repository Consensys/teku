package tech.pegasys.artemis.services.adapter.io;

import io.grpc.BindableService;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.checkerframework.checker.nullness.Opt;
import tech.pegasys.artemis.pow.api.PowEvent;
import tech.pegasys.artemis.services.adapter.io.client.EventForwardingClient;
import tech.pegasys.artemis.services.adapter.io.client.GrpcEventForwardingClient;
import tech.pegasys.artemis.services.adapter.dto.RemoteCallResponse;
import tech.pegasys.artemis.services.adapter.event.EventDescriptor;
import tech.pegasys.artemis.services.adapter.event.OutboundEvent;
import tech.pegasys.artemis.services.adapter.factory.MethodDescriptorFactory;

public class GrpcServiceAdapterIO implements ServiceAdapterIO, BindableService {

  public static final String SERVICE_NAME = "tech.pegasys.artemis.serviceAdapter";

  private Optional<Server> server = Optional.empty();

  private Set<DescriptorAndHandler> inboundDescriptors;

  private Map<String, EventForwardingClient<PowEvent>> outboundClients;

  public GrpcServiceAdapterIO() {

    inboundDescriptors = new HashSet<>();
    outboundClients = new HashMap<>();
  }

  @Override
  public void startServer(int serverPort) throws IOException {
    server = Optional.of(ServerBuilder.forPort(serverPort).addService(this).build());

    server.get().start();
  }

  @Override
  public void shutdownServer() {
    server.ifPresent(theServer ->theServer.shutdown());
  }

  @Override
  public void registerInboundEventEndpoint(
      EventDescriptor eventDescriptor, InboundEventHandler handler) {

    final MethodDescriptor<? extends PowEvent, RemoteCallResponse> methodDescriptor
        = MethodDescriptorFactory.build(SERVICE_NAME, eventDescriptor);

    inboundDescriptors.add(new DescriptorAndHandler(methodDescriptor, handler));
  }

  @Override
  public void registerOutboundEventClient(OutboundEvent outboundEvent) {
    final Channel channel =
        ManagedChannelBuilder.forTarget(outboundEvent.getUrl()).usePlaintext(true).build();

    final MethodDescriptor<? extends PowEvent, RemoteCallResponse> descriptor =
        MethodDescriptorFactory.build(SERVICE_NAME, outboundEvent.getEventDescriptor());

    final EventForwardingClient client = new GrpcEventForwardingClient(channel, descriptor);

    outboundClients.put(outboundEvent.getEventDescriptor().getEventType(), client);
  }

  @Override
  public Optional<EventForwardingClient> getOutboundEventClient(String eventType) {
    return Optional.ofNullable(outboundClients.get(eventType));
  }

  @Override
  public ServerServiceDefinition bindService() {
    final ServerServiceDefinition.Builder ssd = ServerServiceDefinition.builder(SERVICE_NAME);

    inboundDescriptors.forEach(
        descriptorAndHandler ->
            ssd.addMethod(descriptorAndHandler.descriptor,
                ServerCalls.asyncUnaryCall((event, responseObserver) ->
                    onInboundEvent(event, responseObserver, descriptorAndHandler.handler))));

    return ssd.build();
  }

  private void onInboundEvent(PowEvent<?> event,
      StreamObserver<RemoteCallResponse> responseObserver, InboundEventHandler handler) {

    try {
      handler.onInboundEvent(event);
      responseObserver.onNext(new RemoteCallResponse(true));
    } catch (Throwable t) {
      responseObserver.onNext(new RemoteCallResponse(false, t));
    }

    responseObserver.onCompleted();
  }

  private class DescriptorAndHandler {
    private MethodDescriptor<? extends PowEvent, RemoteCallResponse> descriptor;

    private InboundEventHandler handler;

    private DescriptorAndHandler(
        MethodDescriptor<? extends PowEvent, RemoteCallResponse> descriptor,
        InboundEventHandler handler) {
      this.descriptor = descriptor;
      this.handler = handler;
    }
  }

}

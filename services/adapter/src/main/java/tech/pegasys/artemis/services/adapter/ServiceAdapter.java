/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.services.adapter;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
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
import tech.pegasys.artemis.pow.api.PowEvent;
import tech.pegasys.artemis.services.ServiceInterface;
import tech.pegasys.artemis.services.adapter.client.EventForwardingClient;
import tech.pegasys.artemis.services.adapter.client.GrpcEventForwardingClient;
import tech.pegasys.artemis.services.adapter.dto.RemoteCallResponse;
import tech.pegasys.artemis.services.adapter.event.EventDescriptor;
import tech.pegasys.artemis.services.adapter.event.OutboundEvent;
import tech.pegasys.artemis.services.adapter.factory.MethodDescriptorFactory;

public class ServiceAdapter implements ServiceInterface, BindableService {

  public static final String SERVICE_NAME = "tech.pegasys.artemis.serviceAdapter";

  private EventBus eventBus;

  private Set<MethodDescriptor<? extends PowEvent, RemoteCallResponse>> inboundDescriptors;

  private Map<String, EventForwardingClient<PowEvent>> outboundClients;

  private int serverPort;

  private Optional<Server> server;

  public ServiceAdapter(
      int serverPort, Set<EventDescriptor> inboundEvents, Set<OutboundEvent> outboundEvents) {
    this.serverPort = serverPort;

    inboundDescriptors = new HashSet<>();
    outboundClients = new HashMap<>();

    inboundEvents.forEach(inboundEvent -> registerInboundEvent(inboundEvent));

    outboundEvents.forEach(outboundEvent -> registerOutboundEvent(outboundEvent));
  }

  public ServiceAdapter(Set<EventDescriptor> inboundEvents, Set<OutboundEvent> outboundEvents) {
    this(0, inboundEvents, outboundEvents);
  }

  @Override
  public void init(EventBus eventBus) {
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  @Override
  public void run() {

    if (!inboundDescriptors.isEmpty()) {
      server = Optional.of(ServerBuilder.forPort(serverPort).addService(this).build());
      try {
        server.get().start();
      } catch (IOException e) {
        throw new ServiceAdapterException("An error occurred when starting the server", e);
      }
    } else {
      server = Optional.empty();
    }
  }

  @Override
  public void stop() {
    this.eventBus.unregister(this);

    server.ifPresent(theServer -> theServer.shutdown());
  }

  @Subscribe
  public void onEvent(PowEvent event) {

    // If event type is registered as an outbound event, forward
    if (outboundClients.containsKey(event.getType())) {
      outboundClients.get(event.getType()).forwardEvent(event);
    }
  }

  @Override
  public ServerServiceDefinition bindService() {
    final ServerServiceDefinition.Builder ssd = ServerServiceDefinition.builder(SERVICE_NAME);

    inboundDescriptors.forEach(
        descriptor ->
            ssd.addMethod(
                descriptor,
                ServerCalls.asyncUnaryCall(
                    (event, responseObserver) -> onInboundEvent(event, responseObserver))));

    return ssd.build();
  }

  public Set<MethodDescriptor<? extends PowEvent, RemoteCallResponse>> getInboundDescriptors() {
    return inboundDescriptors;
  }

  private void onInboundEvent(
      PowEvent<?> event, StreamObserver<RemoteCallResponse> responseObserver) {

    try {
      eventBus.post(event);
      responseObserver.onNext(new RemoteCallResponse(true));
    } catch (Throwable t) {
      responseObserver.onNext(new RemoteCallResponse(false, t));
    }

    responseObserver.onCompleted();
  }

  private void registerInboundEvent(EventDescriptor<? extends PowEvent> eventDescriptor) {

    inboundDescriptors.add(MethodDescriptorFactory.build(SERVICE_NAME, eventDescriptor));
  }

  private void registerOutboundEvent(OutboundEvent<? extends PowEvent> outboundEvent) {
    final Channel channel =
        ManagedChannelBuilder.forTarget(outboundEvent.getUrl()).usePlaintext(true).build();

    final MethodDescriptor<? extends PowEvent, RemoteCallResponse> descriptor =
        MethodDescriptorFactory.build(SERVICE_NAME, outboundEvent.getEventDescriptor());

    final EventForwardingClient client = new GrpcEventForwardingClient(channel, descriptor);

    outboundClients.put(outboundEvent.getEventDescriptor().getEventType(), client);
  }
}

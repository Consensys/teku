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
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import java.util.HashSet;
import java.util.Set;
import tech.pegasys.artemis.services.ServiceConfig;
import tech.pegasys.artemis.services.ServiceInterface;
import tech.pegasys.artemis.services.adapter.dto.RemoteCallResponse;
import tech.pegasys.artemis.services.adapter.event.OutboundEvent;
import tech.pegasys.artemis.services.adapter.factory.MethodDescriptorFactory;
import tech.pegasys.artemis.services.adapter.io.inbound.DefaultGrpcServer;
import tech.pegasys.artemis.services.adapter.io.inbound.GrpcServer;
import tech.pegasys.artemis.services.adapter.io.outbound.EventForwarder;
import tech.pegasys.artemis.services.adapter.io.outbound.GrpcEventForwarder;

/** Encapsulates receiving/delivering events from/to Artemis microservices */
public class ServiceAdapter implements ServiceInterface {

  public static final String SERVICE_NAME = "tech.pegasys.artemis.serviceAdapter";

  private GrpcServer server;

  private Set<MethodDescriptor<?, RemoteCallResponse>> inboundDescriptors;

  private Set<EventForwarder<?>> eventForwarders;

  private int serverPort;

  private EventBus eventBus;

  private boolean hasRegisteredInboundEvents = false;

  public ServiceAdapter(
      int serverPort, Set<Class<?>> inboundEvents, Set<OutboundEvent<?>> outboundEvents) {
    this.serverPort = serverPort;
    this.eventForwarders = new HashSet<>();
    this.inboundDescriptors = new HashSet<>();

    if (inboundEvents != null && !inboundEvents.isEmpty()) {
      hasRegisteredInboundEvents = true;
    }

    server = createGrpcServer(serverPort);

    inboundEvents.forEach(
        inboundEvent -> server.registerMethodDescriptor(createMethodDescriptor(inboundEvent)));

    outboundEvents.forEach(outboundEvent -> registerEventForwarder(outboundEvent));
  }

  public ServiceAdapter(Set<Class<?>> inboundEvents, Set<OutboundEvent<?>> outboundEvents) {
    this(0, inboundEvents, outboundEvents);
  }

  @Override
  public void init(ServiceConfig config) {
    this.eventBus = config.getEventBus();
    eventForwarders.forEach(forwarder -> forwarder.init(eventBus));
  }

  @Override
  public void run() {
    server.run();
  }

  @Override
  public void stop() {
    server.stop();

    eventForwarders.forEach(forwarder -> forwarder.stop());
  }

  protected MethodDescriptor<?, RemoteCallResponse> createMethodDescriptor(Class<?> eventClass) {
    return MethodDescriptorFactory.build(SERVICE_NAME, eventClass);
  }

  protected GrpcServer createGrpcServer(int serverPort) {
    return new DefaultGrpcServer(SERVICE_NAME, serverPort, this::onInboundEvent);
  }

  private void onInboundEvent(Object event) {
    eventBus.post(event);
  }

  private void registerEventForwarder(OutboundEvent<?> outboundEvent) {
    final Channel channel =
        ManagedChannelBuilder.forTarget(outboundEvent.getUrl()).usePlaintext(true).build();

    final MethodDescriptor<?, RemoteCallResponse> descriptor =
        MethodDescriptorFactory.build(SERVICE_NAME, outboundEvent.getEventClass());

    final EventForwarder<?> forwarder = new GrpcEventForwarder<>(channel, descriptor);

    eventForwarders.add(forwarder);
  }
}

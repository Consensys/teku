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
import io.grpc.MethodDescriptor;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.artemis.pow.api.PowEvent;
import tech.pegasys.artemis.services.ServiceInterface;
import tech.pegasys.artemis.services.adapter.dto.RemoteCallResponse;
import tech.pegasys.artemis.services.adapter.event.EventDescriptor;
import tech.pegasys.artemis.services.adapter.event.OutboundEvent;
import tech.pegasys.artemis.services.adapter.io.GrpcServiceAdapterIO;
import tech.pegasys.artemis.services.adapter.io.ServiceAdapterIO;

public class ServiceAdapter implements ServiceInterface {

  private EventBus eventBus;

  private int serverPort;

  private ServiceAdapterIO serviceAdapterIO;

  private boolean hasRegisteredInboundEvents = false;

  public ServiceAdapter(
      int serverPort, Set<EventDescriptor> inboundEvents, Set<OutboundEvent> outboundEvents) {
    this.serverPort = serverPort;
    this.serviceAdapterIO = buildServiceAdapterIO();

    if (inboundEvents != null && !inboundEvents.isEmpty()) {
      hasRegisteredInboundEvents = true;
    }

    inboundEvents.forEach(inboundEvent ->
        serviceAdapterIO.registerInboundEventEndpoint(
            inboundEvent, (event) -> eventBus.post(event)));

    outboundEvents.forEach(outboundEvent ->
        serviceAdapterIO.registerOutboundEventClient(outboundEvent));
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

    if (hasRegisteredInboundEvents) {
      try {
        serviceAdapterIO.startServer(serverPort);
      } catch (IOException e) {
        throw new ServiceAdapterException("An error occurred when starting the server", e);
      }
    }
  }

  @Override
  public void stop() {
    this.eventBus.unregister(this);

    serviceAdapterIO.shutdownServer();
  }

  @Subscribe
  public void onEvent(PowEvent event) {

    // If event type is registered as an outbound event, forward
    serviceAdapterIO
        .getOutboundEventClient(event.getType())
        .ifPresent(client -> client.forwardEvent(event));
  }

  protected ServiceAdapterIO buildServiceAdapterIO() {
    return new GrpcServiceAdapterIO();
  }
}

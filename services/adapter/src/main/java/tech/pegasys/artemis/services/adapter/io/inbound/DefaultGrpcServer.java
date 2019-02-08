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

package tech.pegasys.artemis.services.adapter.io.inbound;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import tech.pegasys.artemis.services.adapter.ServiceAdapterException;
import tech.pegasys.artemis.services.adapter.dto.RemoteCallResponse;

/** This is the default implementation of a Grpc Server */
public class DefaultGrpcServer implements GrpcServer, BindableService {

  private String serviceName;

  int serverPort;

  private Set<MethodDescriptor<?, RemoteCallResponse>> methodDescriptors;

  private Consumer<Object> eventConsumer;

  private Optional<Server> server = Optional.empty();

  private boolean started = false;

  public DefaultGrpcServer(String serviceName, int serverPort, Consumer<Object> eventConsumer) {
    this.serviceName = serviceName;
    this.serverPort = serverPort;
    this.eventConsumer = eventConsumer;

    methodDescriptors = new HashSet<>();
  }

  @Override
  public void run() {
    try {
      if (!methodDescriptors.isEmpty()) {
        server = Optional.of(ServerBuilder.forPort(serverPort).addService(this).build());

        server.get().start();
        started = true;
      }
    } catch (IOException e) {
      throw new ServiceAdapterException("Error when starting gRPC server", e);
    }
  }

  @Override
  public void stop() {
    server.ifPresent(theServer -> theServer.shutdown());
    started = false;
  }

  @Override
  public void registerMethodDescriptor(MethodDescriptor<?, RemoteCallResponse> methodDescriptor) {
    if (started) {
      throw new ServiceAdapterException("Cannot register method descriptor as server is started");
    }
    methodDescriptors.add(methodDescriptor);
  }

  @Override
  public ServerServiceDefinition bindService() {
    final ServerServiceDefinition.Builder ssd = ServerServiceDefinition.builder(serviceName);

    methodDescriptors.forEach(
        descriptor ->
            ssd.addMethod(
                descriptor,
                ServerCalls.asyncUnaryCall(
                    (event, responseObserver) -> onInboundEvent(event, responseObserver))));

    return ssd.build();
  }

  private void onInboundEvent(Object event, StreamObserver<RemoteCallResponse> responseObserver) {

    try {
      eventConsumer.accept(event);
      responseObserver.onNext(new RemoteCallResponse(true));
    } catch (Throwable t) {
      responseObserver.onNext(new RemoteCallResponse(false, t));
    }

    responseObserver.onCompleted();
  }
}

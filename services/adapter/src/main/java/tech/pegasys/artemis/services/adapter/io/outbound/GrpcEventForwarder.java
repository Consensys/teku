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

package tech.pegasys.artemis.services.adapter.io.outbound;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;
import tech.pegasys.artemis.services.adapter.ServiceAdapterException;
import tech.pegasys.artemis.services.adapter.dto.RemoteCallResponse;

public class GrpcEventForwarder<T> implements EventForwarder<T> {

  private Channel channel;
  private MethodDescriptor<T, RemoteCallResponse> descriptor;
  private EventBus eventBus;

  public GrpcEventForwarder(Channel channel, MethodDescriptor<T, RemoteCallResponse> descriptor) {
    this.channel = channel;
    this.descriptor = descriptor;
  }

  @Override
  public void init(EventBus eventBus) {
    this.eventBus = eventBus;
    eventBus.register(this);
  }

  @Subscribe
  @Override
  public void onEvent(T event) {
    final ClientCall<T, RemoteCallResponse> call = channel.newCall(descriptor, CallOptions.DEFAULT);

    final RemoteCallResponse response = ClientCalls.blockingUnaryCall(call, event);

    if (!response.isSuccess()) {
      throw new ServiceAdapterException(
          "An exception occured during event forwarding", response.getErrorCause());
    }
  }

  @Override
  public void stop() {
    eventBus.unregister(this);
  }
}

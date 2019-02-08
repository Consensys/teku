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

package tech.pegasys.artemis.services.adapter.factory;

import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import tech.pegasys.artemis.services.adapter.dto.RemoteCallResponse;
import tech.pegasys.artemis.services.adapter.marshall.ProtoMarshaller;

public class MethodDescriptorFactory {

  public static <T> MethodDescriptor<T, RemoteCallResponse> build(
      String serviceName, Class<T> eventClass) {

    final MethodDescriptor<T, RemoteCallResponse> descriptor =
        MethodDescriptor.newBuilder(
                new ProtoMarshaller<>(eventClass), new ProtoMarshaller<>(RemoteCallResponse.class))
            .setFullMethodName(
                MethodDescriptor.generateFullMethodName(serviceName, eventClass.getName()))
            .setType(MethodType.UNARY)
            .setSampledToLocalTracing(true)
            .build();

    return descriptor;
  }
}

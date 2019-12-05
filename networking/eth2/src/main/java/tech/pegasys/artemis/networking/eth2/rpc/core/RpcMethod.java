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

package tech.pegasys.artemis.networking.eth2.rpc.core;

import java.util.Objects;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;

public class RpcMethod<I, O> {

  private final String methodMultistreamId;
  private final RpcEncoding encoding;
  private final Class<I> requestType;
  private final Class<O> responseType;

  public RpcMethod(
      final String methodMultistreamId,
      final RpcEncoding encoding,
      final Class<I> requestType,
      final Class<O> responseType) {
    this.methodMultistreamId = methodMultistreamId + "/" + encoding.getName();
    this.encoding = encoding;
    this.requestType = requestType;
    this.responseType = responseType;
  }

  public String getMultistreamId() {
    return methodMultistreamId;
  }

  public Class<I> getRequestType() {
    return requestType;
  }

  public Class<O> getResponseType() {
    return responseType;
  }

  public RpcEncoding getEncoding() {
    return encoding;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RpcMethod<?, ?> rpcMethod = (RpcMethod<?, ?>) o;
    return methodMultistreamId.equals(rpcMethod.methodMultistreamId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(methodMultistreamId);
  }
}

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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc;

import com.google.common.base.MoreObjects;

public class RpcMessage<T> {
  private final int byteConsumed;
  private final T message;

  public RpcMessage(final int byteConsumed, final T message) {
    this.byteConsumed = byteConsumed;
    this.message = message;
  }

  public int getByteConsumed() {
    return byteConsumed;
  }

  public T getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("byteConsumed", byteConsumed)
        .add("message", message)
        .toString();
  }
}

/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.spec.executionengine.client.schema;

public final class Response<T> {

  private final T payload;
  private final String reason;

  private Response(T payload, String reason) {
    this.payload = payload;
    this.reason = reason;
  }

  public Response(String reason) {
    this(null, reason);
  }

  public Response(T payload) {
    this.payload = payload;
    this.reason = null;
  }

  public T getPayload() {
    return payload;
  }

  public String getReason() {
    return reason;
  }
}

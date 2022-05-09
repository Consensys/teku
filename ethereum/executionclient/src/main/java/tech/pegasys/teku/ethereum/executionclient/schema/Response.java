/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionclient.schema;

import java.util.Objects;

public class Response<T> {

  private final T payload;
  private final String errorMessage;

  private Response(T payload, String errorMessage) {
    this.payload = payload;
    this.errorMessage = errorMessage;
  }

  public Response(String errorMessage) {
    this(null, errorMessage);
  }

  public Response(T payload) {
    this.payload = payload;
    this.errorMessage = null;
  }

  public T getPayload() {
    return payload;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Response<?> response = (Response<?>) o;
    return Objects.equals(payload, response.payload)
        && Objects.equals(errorMessage, response.errorMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(payload, errorMessage);
  }
}

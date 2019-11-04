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
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;

public class Response<T> {
  public static final Bytes SUCCESS_RESPONSE_CODE = Bytes.of(0);
  private final Bytes responseCode;
  private final T data;

  public Response(final Bytes responseCode, final T data) {
    this.responseCode = responseCode;
    this.data = data;
  }

  public boolean isSuccess() {
    return SUCCESS_RESPONSE_CODE.equals(responseCode);
  }

  public Bytes getResponseCode() {
    return responseCode;
  }

  public T getData() {
    return data;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Response<?> response = (Response<?>) o;
    return Objects.equals(responseCode, response.responseCode)
        && Objects.equals(data, response.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(responseCode, data);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("responseCode", responseCode)
        .add("data", data)
        .toString();
  }
}

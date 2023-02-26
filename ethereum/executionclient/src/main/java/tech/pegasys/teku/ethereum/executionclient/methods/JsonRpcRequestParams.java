/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.ethereum.executionclient.methods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class JsonRpcRequestParams {

  private final List<Object> params;

  private JsonRpcRequestParams(final List<Object> params) {
    this.params = params;
  }

  public <T> T getRequiredParameter(final int index, final Class<T> paramClass) {
    return getOptionalParameter(index, paramClass)
        .orElseThrow(
            () -> new IllegalArgumentException("Missing required parameter at index " + index));
  }

  @SuppressWarnings("unchecked")
  public <T> Optional<T> getOptionalParameter(final int index, final Class<T> paramClass) {
    if (params == null || params.isEmpty() || params.size() <= index || params.get(index) == null) {
      return Optional.empty();
    }

    final Object param = params.get(index);

    if (!paramClass.isAssignableFrom(param.getClass())) {
      throw new IllegalArgumentException(
          "Invalid type " + paramClass.getSimpleName() + " for parameter at index " + index);
    }

    return Optional.of((T) param);
  }

  public static final JsonRpcRequestParams NO_PARAMS =
      new JsonRpcRequestParams(Collections.emptyList());

  public static class Builder {

    private final List<Object> params = new ArrayList<>();

    public Builder add(Object param) {
      this.params.add(param);
      return this;
    }

    public Builder add(int index, Object param) {
      if (params.get(index) != null) {
        params.remove(index);
      }

      this.params.add(index, param);

      return this;
    }

    public Builder addOptional(Optional<?> param) {
      param.ifPresent(this::add);
      return this;
    }

    public Builder addOptional(int index, Optional<?> param) {
      param.ifPresent(
          p -> {
            if (params.get(index) != null) {
              params.remove(index);
            }

            params.add(index, p);
          });
      return this;
    }

    public JsonRpcRequestParams build() {
      return new JsonRpcRequestParams(params);
    }
  }
}

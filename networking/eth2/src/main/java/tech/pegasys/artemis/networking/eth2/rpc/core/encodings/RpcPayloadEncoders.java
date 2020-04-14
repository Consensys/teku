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

package tech.pegasys.artemis.networking.eth2.rpc.core.encodings;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.snappy.SnappyCompressor;

public class RpcPayloadEncoders implements RpcPayloadEncoderProvider {

  private final Map<Class<?>, RpcPayloadEncoder<?>> encoders;
  private final Function<Class<?>, RpcPayloadEncoder<?>> defaultEncoderProvider;

  private RpcPayloadEncoders(
      final Map<Class<?>, RpcPayloadEncoder<?>> encoders,
      final Function<Class<?>, RpcPayloadEncoder<?>> defaultEncoderProvider) {
    this.encoders = encoders;
    this.defaultEncoderProvider = defaultEncoderProvider;
  }

  public static RpcPayloadEncoders.Builder builder() {
    return new RpcPayloadEncoders.Builder();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> RpcPayloadEncoder<T> getEncoder(final Class<T> clazz) {
    RpcPayloadEncoder<?> encoder = encoders.get(clazz);
    return (RpcPayloadEncoder<T>) (encoder != null ? encoder : defaultEncoderProvider.apply(clazz));
  }

  public static class Builder {
    private Map<Class<?>, RpcPayloadEncoder<?>> encoders = new HashMap<>();
    private Function<Class<?>, RpcPayloadEncoder<?>> defaultEncoderProvider;

    private Builder() {}

    public <T> Builder withEncoder(final Class<T> clazz, final RpcPayloadEncoder<T> encoder) {
      encoders.put(clazz, new SnappyCompressor<>(encoder));
      return this;
    }

    public Builder defaultEncoderProvider(
        final Function<Class<?>, RpcPayloadEncoder<?>> defaultEncoderProvider) {
      this.defaultEncoderProvider = defaultEncoderProvider;
      return this;
    }

    public RpcPayloadEncoderProvider build() {
      checkNotNull(defaultEncoderProvider, "Must provide a default encoder");
      return new RpcPayloadEncoders(Collections.unmodifiableMap(encoders), defaultEncoderProvider);
    }
  }
}

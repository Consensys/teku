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

package org.ethereum.beacon.discovery;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Stateless {@link DataSource} which is capable of converting keys or/and values between different
 * types
 *
 * @param <KeyType> Target type of source keys
 * @param <ValueType> Target type of source values
 * @param <UpKeyType> type of upstream keys
 * @param <UpValueType> type of upstream values
 */
public class CodecSource<KeyType, ValueType, UpKeyType, UpValueType>
    extends AbstractLinkedDataSource<KeyType, ValueType, UpKeyType, UpValueType> {

  private final Function<KeyType, UpKeyType> keyCoder;
  private final Function<ValueType, UpValueType> valueCoder;
  private final Function<UpValueType, ValueType> valueDecoder;

  /**
   * Creates a codec for upstream source with key/value coders/decoders
   *
   * @param upstreamSource Upstream source
   * @param keyCoder Converts target KeyType to upstream UpKeyType. {@link Function#identity()} is
   *     passed if KeyType == UpKeyType and no key conversion wanted
   * @param valueCoder Converts target ValueType to upstream UpValueType if ValueType == UpValueType
   *     and no conversion needed {@link Function#identity()} should be passed
   * @param valueDecoder Converts upstream UpValueType to target ValueType if ValueType ==
   *     UpValueType and no conversion needed {@link Function#identity()} should be passed
   */
  public CodecSource(
      @Nonnull final DataSource<UpKeyType, UpValueType> upstreamSource,
      @Nonnull final Function<KeyType, UpKeyType> keyCoder,
      @Nonnull final Function<ValueType, UpValueType> valueCoder,
      @Nonnull final Function<UpValueType, ValueType> valueDecoder) {
    super(upstreamSource, true);
    this.keyCoder = requireNonNull(keyCoder);
    this.valueCoder = requireNonNull(valueCoder);
    this.valueDecoder = requireNonNull(valueDecoder);
  }

  @Override
  public Optional<ValueType> get(@Nonnull final KeyType key) {
    return getUpstream().get(keyCoder.apply(key)).map(valueDecoder);
  }

  @Override
  public void put(@Nonnull final KeyType key, @Nonnull final ValueType value) {
    getUpstream().put(keyCoder.apply(key), valueCoder.apply(value));
  }

  @Override
  public void remove(@Nonnull final KeyType key) {
    getUpstream().remove(keyCoder.apply(key));
  }

  /** Shortcut {@link CodecSource} subclass when only key conversion is needed */
  public static class KeyOnly<KeyType, ValueType, UpKeyType>
      extends CodecSource<KeyType, ValueType, UpKeyType, ValueType> {
    public KeyOnly(
        @Nonnull final DataSource<UpKeyType, ValueType> upstreamSource,
        @Nonnull final Function<KeyType, UpKeyType> keyCoder) {
      super(upstreamSource, keyCoder, Function.identity(), Function.identity());
    }
  }

  /** Shortcut {@link CodecSource} subclass when only value conversion is needed */
  public static class ValueOnly<KeyType, ValueType, UpValueType>
      extends CodecSource<KeyType, ValueType, KeyType, UpValueType> {
    public ValueOnly(
        @Nonnull final DataSource<KeyType, UpValueType> upstreamSource,
        @Nonnull final Function<ValueType, UpValueType> valueCoder,
        @Nonnull final Function<UpValueType, ValueType> valueDecoder) {
      super(upstreamSource, Function.identity(), valueCoder, valueDecoder);
    }
  }
}

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

package tech.pegasys.artemis.util.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.ssz.SSZTypes.Bytes4;

class ConstantsReader {

  private static final ImmutableMap<Class<?>, Function<String, ?>> PARSERS =
      ImmutableMap.<Class<?>, Function<String, ?>>builder()
          .put(Integer.TYPE, ConstantsReader::parseInt)
          .put(Long.TYPE, Long::valueOf)
          .put(UnsignedLong.class, UnsignedLong::valueOf)
          .put(String.class, Function.identity())
          .put(Bytes.class, Bytes::fromHexString)
          .put(Bytes4.class, Bytes4::fromHexString)
          .build();

  @SuppressWarnings("unchecked")
  public static void loadConstantsFrom(final InputStream source) throws IOException {
    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    final Map<String, String> values =
        (Map<String, String>)
            mapper
                .readerFor(
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class))
                .readValues(source)
                .next();
    values.forEach(ConstantsReader::setField);
  }

  private static void setField(final String key, final String value) {
    try {
      final Field field = Constants.class.getField(key);
      if (!Modifier.isStatic(field.getModifiers())) {
        throw new IllegalArgumentException("Unknown constant: " + key);
      }
      field.set(null, parseValue(field, value));
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalArgumentException("Unknown constant: " + key, e);
    }
  }

  private static Object parseValue(final Field field, final String value) {
    final Function<String, ?> parser = PARSERS.get(field.getType());
    if (parser == null) {
      throw new IllegalArgumentException("Unknown constant type: " + field.getType());
    }
    try {
      return parser.apply(value);
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Failed to parse value '" + value + "' for constant '" + field.getName() + "'");
    }
  }

  private static Integer parseInt(final String value) {
    if (value.startsWith("0x")) {
      return Bytes.fromHexString(value)
          .toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN)
          .intValueExact();
    } else {
      return Integer.valueOf(value);
    }
  }
}

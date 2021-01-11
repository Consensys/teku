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

package tech.pegasys.teku.util.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

class ConstantsReader {

  private static final ImmutableMap<Class<?>, Function<Object, ?>> PARSERS =
      ImmutableMap.<Class<?>, Function<Object, ?>>builder()
          .put(Integer.TYPE, ConstantsReader::parseInt)
          .put(Long.TYPE, toString(Long::valueOf))
          .put(UInt64.class, toString(UInt64::valueOf))
          .put(String.class, Function.identity())
          .put(Bytes.class, toString(Bytes::fromHexString))
          .put(Bytes4.class, toString(Bytes4::fromHexString))
          .put(boolean.class, toString(Boolean::valueOf))
          .build();

  @SuppressWarnings("unchecked")
  public static void loadConstantsFrom(final InputStream source) throws IOException {
    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    final Map<String, Object> values =
        (Map<String, Object>)
            mapper
                .readerFor(
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class))
                .readValues(source)
                .next();
    values.forEach(ConstantsReader::setField);
  }

  private static void setField(final String key, final Object value) {
    try {
      final Field field = Constants.class.getField(key);
      if (!Modifier.isStatic(field.getModifiers())) {
        throw new IllegalArgumentException("Unknown constant: " + key);
      }
      field.set(null, parseValue(field, value));
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalArgumentException("Unknown constant: " + key, e);
    } catch (Throwable t) {
      throw new IllegalArgumentException("Unable to set constant: " + key, t);
    }
  }

  private static Object parseValue(final Field field, final Object value) {
    final Function<Object, ?> parser = PARSERS.get(field.getType());
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

  private static Integer parseInt(final Object input) {
    if (input instanceof Integer) {
      return (Integer) input;
    }
    final String value = input.toString();
    if (value.startsWith("0x")) {
      if (value.length() != 10) {
        throw new IllegalArgumentException("Little-endian constant is not four bytes: " + value);
      }
      return Integer.reverseBytes(Integer.decode(value));
    } else {
      return Integer.valueOf(value);
    }
  }

  private static <T> Function<Object, T> toString(final Function<String, T> function) {
    return value -> function.apply(value.toString());
  }
}

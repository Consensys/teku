/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.json.types;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

public class EnumTypeDefinition<T extends Enum<T>> extends PrimitiveTypeDefinition<T> {
  final Class<T> itemType;
  private final Function<T, String> serializer;

  public EnumTypeDefinition(final Class<T> itemType) {
    this(itemType, Objects::toString);
  }

  public EnumTypeDefinition(final Class<T> itemType, final Function<T, String> serializer) {
    this.itemType = itemType;
    this.serializer = serializer;
  }

  @Override
  public T deserialize(final JsonParser parser) throws IOException {
    return deserializeFromString(parser.getValueAsString());
  }

  @Override
  public void serializeOpenApiTypeFields(final JsonGenerator gen) throws IOException {
    gen.writeStringField("type", "string");
    gen.writeArrayFieldStart("enum");

    for (T value : itemType.getEnumConstants()) {
      gen.writeString(serializeToString(value));
    }
    gen.writeEndArray();
  }

  @Override
  public void serialize(final T value, final JsonGenerator gen) throws IOException {
    gen.writeString(serializeToString(value));
  }

  @Override
  public String serializeToString(final T value) {
    return value != null ? serializer.apply(value) : null;
  }

  @Override
  public T deserializeFromString(final String value) {
    for (T t : itemType.getEnumConstants()) {
      if (t.toString().equalsIgnoreCase(value)) {
        return t;
      }
    }
    throw new IllegalArgumentException("Unknown enum value: " + value);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EnumTypeDefinition<?> that = (EnumTypeDefinition<?>) o;
    return Objects.equals(itemType, that.itemType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemType);
  }
}

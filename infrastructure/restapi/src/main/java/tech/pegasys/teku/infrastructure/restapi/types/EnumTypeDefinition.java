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

package tech.pegasys.teku.infrastructure.restapi.types;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;

public class EnumTypeDefinition<T extends Enum<T>> implements DeserializableTypeDefinition<T> {
  final Class<T> itemType;

  public EnumTypeDefinition(final Class<T> itemType) {
    this.itemType = itemType;
  }

  @Override
  public T deserialize(final JsonParser parser) throws IOException {
    return Enum.valueOf(itemType, parser.getValueAsString());
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("type", "string");
    gen.writeArrayFieldStart("enum");

    for (T value : itemType.getEnumConstants()) {
      gen.writeString(value.name());
    }
    gen.writeEndArray();
    gen.writeEndObject();
  }

  @Override
  public void serialize(final T value, final JsonGenerator gen) throws IOException {
    gen.writeString(value.name());
  }
}

/*
 * Copyright 2022 ConsenSys AG.
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
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class DeserializableStringMapTypeDefinition
    extends SerializableObjectTypeDefinition<Map<String, String>>
    implements DeserializableTypeDefinition<Map<String, String>> {

  public DeserializableStringMapTypeDefinition() {
    this(Optional.empty(), Optional.empty(), Optional.empty());
  }

  public DeserializableStringMapTypeDefinition(
      final Optional<String> name,
      final Optional<String> title,
      final Optional<String> description) {
    super(name, title, description, Map.of());
  }

  @Override
  public Map<String, String> deserialize(final JsonParser p) throws IOException {
    final Map<String, String> result = new TreeMap<>();
    JsonToken t = p.getCurrentToken();
    if (t == null) {
      t = p.nextToken();
    }
    if (t == JsonToken.START_OBJECT) {
      t = p.nextToken();
    }
    for (; t == JsonToken.FIELD_NAME; t = p.nextToken()) {
      final String fieldName = p.getCurrentName();
      p.nextToken();
      final String fieldValue = p.getValueAsString();
      result.put(fieldName, fieldValue);
    }
    return result;
  }

  @Override
  public void serialize(final Map<String, String> value, final JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();
    for (Map.Entry<String, String> entry : value.entrySet()) {
      gen.writeStringField(entry.getKey(), entry.getValue());
    }
    gen.writeEndObject();
  }

  @Override
  public DeserializableTypeDefinition<Map<String, String>> withDescription(
      final String description) {
    return new DeserializableStringMapTypeDefinition(
        Optional.empty(), getTitle(), Optional.of(description));
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("type", "object");
    if (getTitle().isPresent()) {
      gen.writeStringField("title", getTitle().get());
    }
    if (getDescription().isPresent()) {
      gen.writeStringField("description", getDescription().get());
    }
    gen.writeObjectFieldStart("additionalProperties");
    gen.writeStringField("type", "string");
    gen.writeEndObject();
    gen.writeEndObject();
  }
}

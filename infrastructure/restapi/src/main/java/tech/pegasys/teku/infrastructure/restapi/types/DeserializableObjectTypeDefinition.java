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

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.MoreObjects;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class DeserializableObjectTypeDefinition<TObject> implements DeserializableTypeDefinition<TObject> {
  private static final Logger LOG = LogManager.getLogger();
  private final Optional<String> name;
  private final Map<String, FieldDefinition<TObject>> fields;
  private final Supplier<TObject> initializer;

  DeserializableObjectTypeDefinition(
      final Optional<String> name,
      final Supplier<TObject> initializer,
      final Map<String, FieldDefinition<TObject>> fields) {
    this.name = name;
    this.initializer = initializer;
    this.fields = fields;
  }

  @Override
  public Optional<String> getTypeName() {
    return name;
  }

  @Override
  public void serialize(final TObject value, final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    for (FieldDefinition<TObject> field : fields.values()) {
      field.writeField(value, gen);
    }
    gen.writeEndObject();
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    if (name.isPresent()) {
      gen.writeStringField("title", name.get());
    }
    gen.writeStringField("type", "object");
    gen.writeObjectFieldStart("properties");
    for (FieldDefinition<TObject> field : fields.values()) {
      field.writeOpenApiField(gen);
    }
    gen.writeEndObject();
    gen.writeEndObject();
  }

  @Override
  public TObject deserialize(final JsonParser p) throws IOException {
    TObject result = initializer.get();
    JsonToken t = p.getCurrentToken();
    if (t == null) {
      t = p.nextToken();
    }
    if (t == JsonToken.START_OBJECT) {
      t = p.nextToken();
    }
    for (; t == JsonToken.FIELD_NAME; t = p.nextToken()) {
      String fieldName = p.getCurrentName();
      p.nextToken();
      final FieldDefinition<TObject> objectField = fields.get(fieldName);
      if (objectField != null) {
        objectField.readField(result, p);
      } else {
        LOG.debug("Unknown field: {}", fieldName);
      }
    }

    return result;
  }

  @Override
  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return fields.values().stream()
        .flatMap(field -> field.getReferencedTypeDefinitions().stream())
        .collect(toSet());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("name", name).add("fields", fields).toString();
  }

  interface FieldDefinition<TObject> {
    void writeField(final TObject source, JsonGenerator gen) throws IOException;

    void writeOpenApiField(JsonGenerator gen) throws IOException;

    void readField(TObject target, JsonParser parser) throws IOException;

    Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions();
  }
}

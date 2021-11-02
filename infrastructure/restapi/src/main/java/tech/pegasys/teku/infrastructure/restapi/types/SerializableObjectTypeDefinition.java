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
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

class SerializableObjectTypeDefinition<TObject> implements SerializableTypeDefinition<TObject> {

  private final Optional<String> name;
  private final Map<String, FieldDefinition<TObject>> fields;

  SerializableObjectTypeDefinition(
      final Optional<String> name, final Map<String, FieldDefinition<TObject>> fields) {
    this.name = name;
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

  interface FieldDefinition<TObject> {
    void writeField(final TObject source, JsonGenerator gen) throws IOException;

    void writeOpenApiField(JsonGenerator gen) throws IOException;
  }
}

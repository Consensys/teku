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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class SerializableObjectTypeDefinitionBuilder<TObject> {

  private final Map<String, ObjectFieldDefinition<TObject, ?>> fields = new LinkedHashMap<>();

  public static <TObject> SerializableObjectTypeDefinitionBuilder<TObject> objectTypeFor(
      @SuppressWarnings("unused") final Class<TObject> clazz) {
    return new SerializableObjectTypeDefinitionBuilder<>();
  }

  private SerializableObjectTypeDefinitionBuilder() {}

  public <TField> SerializableObjectTypeDefinitionBuilder<TObject> withField(
      final String name,
      final SerializableTypeDefinition<TField> type,
      final Function<TObject, TField> getter) {
    this.fields.put(name, new ObjectFieldDefinition<>(name, getter, type));
    return this;
  }

  public SerializableTypeDefinition<TObject> build() {
    return new SerializableObjectTypeDefinition<>(fields);
  }

  private static class SerializableObjectTypeDefinition<TObject>
      implements SerializableTypeDefinition<TObject> {
    private final Map<String, ObjectFieldDefinition<TObject, ?>> fields;

    private SerializableObjectTypeDefinition(
        final Map<String, ObjectFieldDefinition<TObject, ?>> fields) {
      this.fields = fields;
    }

    @Override
    public void serialize(final TObject value, final JsonGenerator gen) throws IOException {
      gen.writeStartObject();
      for (ObjectFieldDefinition<TObject, ?> field : fields.values()) {
        gen.writeFieldName(field.name);
        field.serialize(value, gen);
      }
      gen.writeEndObject();
    }

    @Override
    public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("type", "object");
      gen.writeObjectFieldStart("properties");
      for (ObjectFieldDefinition<?, ?> field : fields.values()) {
        gen.writeFieldName(field.name);
        field.type.serializeOpenApiTypeOrReference(gen);
      }
      gen.writeEndObject();
      gen.writeEndObject();
    }
  }

  private static class ObjectFieldDefinition<TObject, TField> {
    private final String name;

    private final Function<TObject, TField> getter;
    private final SerializableTypeDefinition<TField> type;

    private ObjectFieldDefinition(
        final String name,
        final Function<TObject, TField> getter,
        final SerializableTypeDefinition<TField> type) {
      this.name = name;
      this.getter = getter;
      this.type = type;
    }

    public void serialize(final TObject source, final JsonGenerator gen) throws IOException {
      type.serialize(getter.apply(source), gen);
    }
  }
}

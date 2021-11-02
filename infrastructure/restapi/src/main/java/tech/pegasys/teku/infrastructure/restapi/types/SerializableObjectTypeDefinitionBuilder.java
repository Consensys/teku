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
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.restapi.types.SerializableObjectTypeDefinition.FieldDefinition;

public class SerializableObjectTypeDefinitionBuilder<TObject> {

  private final Map<String, FieldDefinition<TObject>> fields = new LinkedHashMap<>();
  private Optional<String> name = Optional.empty();

  SerializableObjectTypeDefinitionBuilder() {}

  public SerializableObjectTypeDefinitionBuilder<TObject> name(final String name) {
    this.name = Optional.of(name);
    return this;
  }

  public <TField> SerializableObjectTypeDefinitionBuilder<TObject> withField(
      final String name,
      final SerializableTypeDefinition<TField> type,
      final Function<TObject, TField> getter) {
    this.fields.put(name, new RequiredFieldDefinition<>(name, getter, type));
    return this;
  }

  public <TField> SerializableObjectTypeDefinitionBuilder<TObject> withOptionalField(
      final String name,
      final SerializableTypeDefinition<TField> type,
      final Function<TObject, Optional<TField>> getter) {
    this.fields.put(name, new OptionalFieldDefinition<>(name, getter, type));
    return this;
  }

  public SerializableTypeDefinition<TObject> build() {
    return new SerializableObjectTypeDefinition<>(name, fields);
  }

  private static class RequiredFieldDefinition<TObject, TField>
      implements FieldDefinition<TObject> {
    private final String name;
    private final Function<TObject, TField> getter;
    private final SerializableTypeDefinition<TField> type;

    private RequiredFieldDefinition(
        final String name,
        final Function<TObject, TField> getter,
        final SerializableTypeDefinition<TField> type) {
      this.name = name;
      this.getter = getter;
      this.type = type;
    }

    @Override
    public void writeField(final TObject source, final JsonGenerator gen) throws IOException {
      gen.writeFieldName(name);
      type.serialize(getter.apply(source), gen);
    }

    @Override
    public void writeOpenApiField(final JsonGenerator gen) throws IOException {
      gen.writeFieldName(name);
      type.serializeOpenApiTypeOrReference(gen);
    }
  }

  private static class OptionalFieldDefinition<TObject, TField>
      implements FieldDefinition<TObject> {
    private final String name;
    private final Function<TObject, Optional<TField>> getter;
    private final SerializableTypeDefinition<TField> type;

    private OptionalFieldDefinition(
        final String name,
        final Function<TObject, Optional<TField>> getter,
        final SerializableTypeDefinition<TField> type) {
      this.name = name;
      this.getter = getter;
      this.type = type;
    }

    @Override
    public void writeField(final TObject source, final JsonGenerator gen) throws IOException {
      final Optional<TField> maybeValue = getter.apply(source);
      if (maybeValue.isPresent()) {
        gen.writeFieldName(name);
        type.serialize(maybeValue.get(), gen);
      }
    }

    @Override
    public void writeOpenApiField(final JsonGenerator gen) throws IOException {
      gen.writeFieldName(name);
      type.serializeOpenApiTypeOrReference(gen);
    }
  }
}

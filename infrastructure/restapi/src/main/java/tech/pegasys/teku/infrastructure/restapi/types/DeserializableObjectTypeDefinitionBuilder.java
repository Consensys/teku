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

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.restapi.types.DeserializableObjectTypeDefinition.FieldDefinition;

public class DeserializableObjectTypeDefinitionBuilder<TObject> {

  private final Map<String, FieldDefinition<TObject>> fields = new LinkedHashMap<>();
  private Optional<String> name = Optional.empty();
  private Supplier<TObject> initializer;

  DeserializableObjectTypeDefinitionBuilder() {}

  public DeserializableObjectTypeDefinitionBuilder<TObject> name(final String name) {
    this.name = Optional.of(name);
    return this;
  }

  public DeserializableObjectTypeDefinitionBuilder<TObject> initializer(
      Supplier<TObject> initializer) {
    this.initializer = initializer;
    return this;
  }

  public <TField> DeserializableObjectTypeDefinitionBuilder<TObject> withField(
      final String name,
      final DeserializableTypeDefinition<TField> type,
      final Function<TObject, TField> getter,
      final BiConsumer<TObject, TField> setter) {
    this.fields.put(name, new RequiredFieldDefinition<>(name, getter, setter, type));
    return this;
  }

  public DeserializableTypeDefinition<TObject> build() {
    checkNotNull(initializer);
    return new DeserializableObjectTypeDefinition<>(name, initializer, fields);
  }

  private static class RequiredFieldDefinition<TObject, TField>
      implements FieldDefinition<TObject> {
    private final String name;
    private final Function<TObject, TField> getter;
    private final BiConsumer<TObject, TField> setter;
    private final DeserializableTypeDefinition<TField> type;

    private RequiredFieldDefinition(
        final String name,
        final Function<TObject, TField> getter,
        final BiConsumer<TObject, TField> setter,
        final DeserializableTypeDefinition<TField> type) {
      this.name = name;
      this.getter = getter;
      this.setter = setter;
      this.type = type;
    }

    @Override
    public void writeField(final TObject source, final JsonGenerator gen) throws IOException {
      gen.writeFieldName(name);
      type.serialize(getter.apply(source), gen);
    }

    @Override
    public void readField(final TObject target, final JsonParser parser) throws IOException {
      final TField value = type.deserialize(parser);
      setter.accept(target, value);
    }

    @Override
    public void writeOpenApiField(final JsonGenerator gen) throws IOException {
      gen.writeFieldName(name);
      type.serializeOpenApiTypeOrReference(gen);
    }

    @Override
    public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
      return type.getSelfAndReferencedTypeDefinitions();
    }
  }
}

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
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ObjectTypeDefinition<TObject, TBuilder extends InternalTypeBuilder<TObject>>
    implements TypeDefinition<TObject> {

  private final String typeName;
  private final Map<String, ObjectField<TObject, TBuilder, ?>> fields;
  private final JsonDeserializer<TObject> deserializer;
  private final JsonSerializer<TObject> serializer;

  public ObjectTypeDefinition(
      final String typeName,
      final Map<String, ObjectField<TObject, TBuilder, ?>> fields,
      final JsonDeserializer<TObject> deserializer,
      final JsonSerializer<TObject> serializer) {
    this.typeName = typeName;
    this.fields = fields;
    this.deserializer = deserializer;
    this.serializer = serializer;
  }

  @Override
  public Optional<String> getTypeName() {
    return Optional.of(typeName);
  }

  @Override
  public JsonDeserializer<TObject> getDeserializer() {
    return deserializer;
  }

  @Override
  public JsonSerializer<TObject> getSerializer() {
    return serializer;
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen, final SerializerProvider serializers)
      throws IOException {
    gen.writeStartObject();
    gen.writeStringField("type", "object");
    gen.writeObjectFieldStart("properties");
    for (final Map.Entry<String, ObjectField<TObject, TBuilder, ?>> fieldEntry :
        fields.entrySet()) {
      gen.writeFieldName(fieldEntry.getKey());
      fieldEntry.getValue().fieldType.serializeOpenApiTypeOrReference(gen, serializers);
    }
    gen.writeEndObject();
    gen.writeEndObject();
  }

  public static class ObjectTypeDefinitionBuilder<T, B extends InternalTypeBuilder<T>> {
    private final Supplier<B> builderFactory;
    private final String typeName;
    private final Map<String, ObjectField<T, B, ?>> fields = new LinkedHashMap<>();

    public ObjectTypeDefinitionBuilder(final Supplier<B> builderFactory, final String typeName) {
      this.builderFactory = builderFactory;
      this.typeName = typeName;
    }

    public <TField> ObjectTypeDefinitionBuilder<T, B> withField(
        final String name,
        final String description,
        final TypeDefinition<TField> fieldType,
        final Function<T, TField> getter,
        final BiConsumer<B, TField> setter) {
      fields.put(name, new ObjectField<>(name, description, fieldType, getter, setter));
      return this;
    }

    public <TField> ObjectTypeDefinitionBuilder<T, B> withField(
        final String name,
        final TypeDefinition<TField> fieldType,
        final Function<T, TField> getter) {
      return withField(name, null, fieldType, getter, null);
    }

    public TypeDefinition<T> build() {
      final JsonDeserializer<T> deserializer = new ObjectDeserializer<>(builderFactory, fields);
      final JsonSerializer<T> serializer = new ObjectSerializer<>(fields);
      return new ObjectTypeDefinition<>(typeName, fields, deserializer, serializer);
    }

    private static class ObjectSerializer<T, B extends InternalTypeBuilder<T>>
        extends JsonSerializer<T> {

      private final Map<String, ObjectField<T, B, ?>> fields;

      public ObjectSerializer(final Map<String, ObjectField<T, B, ?>> fields) {
        this.fields = fields;
      }

      @Override
      public void serialize(
          final T value, final JsonGenerator gen, final SerializerProvider serializers)
          throws IOException {
        gen.writeStartObject();
        for (ObjectField<T, B, ?> field : fields.values()) {
          gen.writeFieldName(field.name);
          field.serialize(value, gen, serializers);
        }
        gen.writeEndObject();
      }
    }
  }

  public static class ObjectField<TObject, TBuilder extends InternalTypeBuilder<TObject>, TField> {
    final String name;
    final String description;
    final TypeDefinition<TField> fieldType;
    final Function<TObject, TField> getter;
    final BiConsumer<TBuilder, TField> setter;

    public ObjectField(
        final String name,
        final String description,
        final TypeDefinition<TField> fieldType,
        final Function<TObject, TField> getter,
        final BiConsumer<TBuilder, TField> setter) {
      this.name = name;
      this.description = description;
      this.fieldType = fieldType;
      this.getter = getter;
      this.setter = setter;
    }

    public void serialize(
        final TObject source, final JsonGenerator gen, final SerializerProvider serializers)
        throws IOException {
      final TField value = getter.apply(source);
      fieldType.getSerializer().serialize(value, gen, serializers);
    }

    public void deserialize(
        final TBuilder builder, final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      final TField value = fieldType.getDeserializer().deserialize(p, ctxt);
      setter.accept(builder, value);
    }
  }
}

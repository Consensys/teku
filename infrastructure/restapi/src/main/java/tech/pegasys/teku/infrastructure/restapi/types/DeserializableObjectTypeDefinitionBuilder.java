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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DeserializableObjectTypeDefinitionBuilder<TObject> {
  private final Map<String, DeserializableFieldDefinition<TObject>> fields = new LinkedHashMap<>();
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
    this.fields.put(name, new RequiredDeserializableFieldDefinition<>(name, getter, setter, type));
    return this;
  }

  public <TField> DeserializableObjectTypeDefinitionBuilder<TObject> withOptionalField(
      final String name,
      final DeserializableTypeDefinition<TField> type,
      final Function<TObject, Optional<TField>> getter,
      final BiConsumer<TObject, Optional<TField>> setter) {
    this.fields.put(name, new OptionalDeserializableFieldDefinition<>(name, getter, setter, type));
    return this;
  }

  public DeserializableTypeDefinition<TObject> build() {
    checkNotNull(initializer, "Must specify an initializer");
    return new DeserializableObjectTypeDefinition<>(name, initializer, fields);
  }
}

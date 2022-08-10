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
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public class SerializableArrayTypeDefinition<ItemT, CollectionT extends Iterable<ItemT>>
    implements SerializableTypeDefinition<CollectionT> {
  private final SerializableTypeDefinition<ItemT> itemType;
  private final Optional<String> description;

  public SerializableArrayTypeDefinition(final SerializableTypeDefinition<ItemT> itemType) {
    this.itemType = itemType;
    this.description = Optional.empty();
  }

  public SerializableArrayTypeDefinition(
      final SerializableTypeDefinition<ItemT> itemType, final String description) {
    this.itemType = itemType;
    this.description = Optional.of(description);
  }

  @Override
  public void serialize(final CollectionT values, final JsonGenerator gen) throws IOException {
    gen.writeStartArray();
    for (ItemT value : values) {
      itemType.serialize(value, gen);
    }
    gen.writeEndArray();
  }

  @Override
  public SerializableTypeDefinition<CollectionT> withDescription(final String description) {
    return new SerializableArrayTypeDefinition<>(itemType, description);
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("type", "array");
    if (description.isPresent()) {
      gen.writeStringField("description", description.get());
    }
    gen.writeFieldName("items");
    itemType.serializeOpenApiTypeOrReference(gen);
    gen.writeEndObject();
  }

  @Override
  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return itemType.getSelfAndReferencedTypeDefinitions();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SerializableArrayTypeDefinition<?, ?> that = (SerializableArrayTypeDefinition<?, ?>) o;
    return Objects.equals(itemType, that.itemType) && Objects.equals(description, that.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemType, description);
  }
}

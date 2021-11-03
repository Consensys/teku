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
import java.util.Collection;
import java.util.List;

public class SerializableArrayTypeDefinition<T> implements SerializableTypeDefinition<List<T>> {
  private final SerializableTypeDefinition<T> itemType;

  public SerializableArrayTypeDefinition(final SerializableTypeDefinition<T> itemType) {
    this.itemType = itemType;
  }

  @Override
  public void serialize(final List<T> values, final JsonGenerator gen) throws IOException {
    gen.writeStartArray();
    for (T value : values) {
      itemType.serialize(value, gen);
    }
    gen.writeEndArray();
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("type", "array");
    gen.writeFieldName("items");
    itemType.serializeOpenApiTypeOrReference(gen);
    gen.writeEndObject();
  }

  @Override
  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return itemType.getSelfAndReferencedTypeDefinitions();
  }
}

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

package tech.pegasys.teku.infrastructure.restapi.openapi;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.OpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;

public class JsonContentTypeDefinition<T> implements ContentTypeDefinition<T> {
  private final SerializableTypeDefinition<T> typeDefinition;

  public JsonContentTypeDefinition(final SerializableTypeDefinition<T> typeDefinition) {
    this.typeDefinition = typeDefinition;
  }

  @Override
  public void serialize(final T value, final OutputStream out) throws IOException {
    JsonUtil.serializeToBytes(value, typeDefinition, out);
  }

  @Override
  public SerializableTypeDefinition<T> withDescription(final String description) {
    return typeDefinition.withDescription(description);
  }

  @Override
  public Optional<String> getTypeName() {
    return typeDefinition.getTypeName();
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    typeDefinition.serializeOpenApiType(gen);
  }

  @Override
  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return typeDefinition.getReferencedTypeDefinitions();
  }

  @Override
  public Collection<OpenApiTypeDefinition> getSelfAndReferencedTypeDefinitions() {
    return typeDefinition.getSelfAndReferencedTypeDefinitions();
  }

  @Override
  public boolean isEquivalentToDeserializableType(final DeserializableTypeDefinition<?> type) {
    return typeDefinition.isEquivalentToDeserializableType(type);
  }

  @Override
  public void serializeOpenApiTypeOrReference(final JsonGenerator gen) throws IOException {
    typeDefinition.serializeOpenApiTypeOrReference(gen);
  }
}

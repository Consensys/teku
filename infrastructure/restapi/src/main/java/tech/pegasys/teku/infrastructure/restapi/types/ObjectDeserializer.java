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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.restapi.types.ObjectTypeDefinition.ObjectField;

class ObjectDeserializer<T, B extends InternalTypeBuilder<T>> extends JsonDeserializer<T> {

  private final Supplier<B> builderFactory;
  private final Map<String, ObjectField<T, B, ?>> fields;

  public ObjectDeserializer(
      final Supplier<B> builderFactory, final Map<String, ObjectField<T, B, ?>> fields) {
    this.builderFactory = builderFactory;
    this.fields = fields;
  }

  @Override
  public T deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException {
    final B builder = builderFactory.get();

    JsonToken t = p.getCurrentToken();
    if (t == null) {
      t = p.nextToken();
    }
    if (t == JsonToken.START_OBJECT) {
      t = p.nextToken();
    }
    for (; t == JsonToken.FIELD_NAME; t = p.nextToken()) {
      String fieldName = p.getCurrentName();
      t = p.nextToken();
      final ObjectField<T, B, ?> objectField = fields.get(fieldName);
      if (objectField != null) {
        objectField.deserialize(builder, p, ctxt);
      } else {
        // Implement policy for unknown fields, ignore??
      }
    }
    return builder.build();
  }
}

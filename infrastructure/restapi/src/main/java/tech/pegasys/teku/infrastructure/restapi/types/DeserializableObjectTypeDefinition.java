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

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.restapi.exceptions.MissingRequiredFieldException;

class DeserializableObjectTypeDefinition<TObject> extends SerializableObjectTypeDefinition<TObject>
    implements DeserializableTypeDefinition<TObject> {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<String, DeserializableFieldDefinition<TObject>> deserializableFields;
  private final Supplier<TObject> initializer;

  DeserializableObjectTypeDefinition(
      final Optional<String> name,
      final Supplier<TObject> initializer,
      final Map<String, DeserializableFieldDefinition<TObject>> fields) {
    super(name, fields);
    this.initializer = initializer;
    this.deserializableFields = fields;
  }

  @Override
  public TObject deserialize(final JsonParser p) throws IOException {
    final TObject result = initializer.get();
    JsonToken t = p.getCurrentToken();
    if (t == null) {
      t = p.nextToken();
    }
    if (t == JsonToken.START_OBJECT) {
      t = p.nextToken();
    }
    final Set<String> presentFields = new HashSet<>();
    for (; t == JsonToken.FIELD_NAME; t = p.nextToken()) {
      String fieldName = p.getCurrentName();
      p.nextToken();
      final DeserializableFieldDefinition<TObject> objectField =
          deserializableFields.get(fieldName);
      if (objectField != null) {
        objectField.readField(result, p);
        presentFields.add(fieldName);
      } else {
        LOG.debug("Unknown field: {}", fieldName);
      }
    }

    final List<String> missingRequiredFields =
        deserializableFields.keySet().stream()
            .filter(
                key -> !presentFields.contains(key) && deserializableFields.get(key).isRequired())
            .collect(Collectors.toList());
    if (!missingRequiredFields.isEmpty()) {
      throw new MissingRequiredFieldException(
          "required fields: ("
              + missingRequiredFields.stream().collect(Collectors.joining(", "))
              + ") were not set");
    }

    return result;
  }

  @Override
  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return deserializableFields.values().stream()
        .flatMap(field -> field.getReferencedTypeDefinitions().stream())
        .collect(toSet());
  }
}

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
import java.io.IOException;
import java.util.List;
import tech.pegasys.teku.infrastructure.restapi.types.StringBasedPrimitiveTypeDefinition.StringTypeBuilder;

public interface DeserializableTypeDefinition<TObject> extends SerializableTypeDefinition<TObject> {

  TObject deserialize(JsonParser parser) throws IOException;

  static <TObject> StringTypeBuilder<TObject> string(
      @SuppressWarnings("unused") final Class<TObject> clazz) {
    return new StringTypeBuilder<>();
  }

  static <TObject> DeserializableTypeDefinition<List<TObject>> listOf(
      final DeserializableTypeDefinition<TObject> itemType) {
    return new DeserializableArrayTypeDefinition<>(itemType);
  }

  static <TObject extends Enum<TObject>> DeserializableTypeDefinition<TObject> enumOf(
      final Class<TObject> itemType) {
    return new EnumTypeDefinition<>(itemType);
  }

  static <TObject> DeserializableObjectTypeDefinitionBuilder<TObject> object(
      @SuppressWarnings("unused") final Class<TObject> type) {
    return object();
  }

  static <TObject> DeserializableObjectTypeDefinitionBuilder<TObject> object() {
    return new DeserializableObjectTypeDefinitionBuilder<>();
  }
}

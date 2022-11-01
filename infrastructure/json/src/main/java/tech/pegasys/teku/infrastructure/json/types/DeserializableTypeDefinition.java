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

import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.json.types.StringBasedPrimitiveTypeDefinition.StringTypeBuilder;

public interface DeserializableTypeDefinition<TObject> extends SerializableTypeDefinition<TObject> {

  TObject deserialize(JsonParser parser) throws IOException;

  @Override
  DeserializableTypeDefinition<TObject> withDescription(final String description);

  static <TObject> StringTypeBuilder<TObject> string(
      @SuppressWarnings("unused") final Class<TObject> clazz) {
    return new StringTypeBuilder<>();
  }

  static <TObject> DeserializableTypeDefinition<List<TObject>> listOf(
      final DeserializableTypeDefinition<TObject> itemType) {
    return new DeserializableListTypeDefinition<>(itemType);
  }

  static DeserializableTypeDefinition<Map<String, String>> mapOfStrings() {
    return mapOf(CoreTypes.STRING_TYPE, CoreTypes.STRING_TYPE, TreeMap::new);
  }

  static <TKey, TValue> DeserializableTypeDefinition<Map<TKey, TValue>> mapOf(
      final StringValueTypeDefinition<TKey> keyType,
      final DeserializableTypeDefinition<TValue> valueType,
      final Supplier<Map<TKey, TValue>> mapConstructor) {
    return new DeserializableMapTypeDefinition<>(keyType, valueType, mapConstructor);
  }

  static <TObject extends Enum<TObject>> DeserializableTypeDefinition<TObject> enumOf(
      final Class<TObject> itemType) {
    return new EnumTypeDefinition<>(itemType);
  }

  static <TObject extends Enum<TObject>> DeserializableTypeDefinition<TObject> enumOf(
      final Class<TObject> itemType, final Function<TObject, String> serializer) {
    return new EnumTypeDefinition<>(itemType, serializer);
  }

  static <TObject extends Enum<TObject>> DeserializableTypeDefinition<TObject> enumOf(
      final Class<TObject> itemType,
      final Function<TObject, String> serializer,
      final Set<TObject> excludedEnumerations) {
    return new EnumTypeDefinition<>(itemType, serializer, excludedEnumerations);
  }

  static <TObject> DeserializableObjectTypeDefinitionBuilder<TObject, TObject> object(
      @SuppressWarnings("unused") final Class<TObject> type) {
    final DeserializableObjectTypeDefinitionBuilder<TObject, TObject> typeBuilder = object();
    return typeBuilder.finisher(Function.identity());
  }

  static <TObject, TBuilder> DeserializableObjectTypeDefinitionBuilder<TObject, TBuilder> object(
      @SuppressWarnings("unused") final Class<TObject> type,
      @SuppressWarnings("unused") final Class<TBuilder> builderType) {
    return object();
  }

  static <TObject, TBuilder> DeserializableObjectTypeDefinitionBuilder<TObject, TBuilder> object() {
    return new DeserializableObjectTypeDefinitionBuilder<>();
  }
}

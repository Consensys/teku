/*
 * Copyright ConsenSys Software Inc., 2023
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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class DeserializableOneOfTypeDefinition<TObject, TBuilder>
    extends SerializableOneOfTypeDefinition<TObject> {

  private final Map<Predicate<String>, DeserializableTypeDefinition<? extends TObject>> parserTypes;

  DeserializableOneOfTypeDefinition(
      Optional<String> name,
      Optional<String> title,
      Optional<String> description,
      Map<Predicate<TObject>, DeserializableTypeDefinition<? extends TObject>> types,
      Map<Predicate<String>, DeserializableTypeDefinition<? extends TObject>> parserTypes) {
    super(name, title, description, types);
    this.parserTypes = parserTypes;
  }

  static <TObject, TBuilder> DeserializableOneOfTypeDefinitionBuilder<TObject, TBuilder> object(
      @SuppressWarnings("unused") final Class<TObject> type,
      @SuppressWarnings("unused") final Class<TBuilder> builderType) {
    return object();
  }

  static <TObject, TBuilder> DeserializableOneOfTypeDefinitionBuilder<TObject, TBuilder> object() {
    return new DeserializableOneOfTypeDefinitionBuilder<>();
  }

  public DeserializableTypeDefinition<? extends TObject> getMatchingType(String json) {
    DeserializableTypeDefinition<? extends TObject> typeDefinition = null;
    for (Predicate<String> predicate : parserTypes.keySet()) {
      if (predicate.test(json)) {
        typeDefinition = parserTypes.get(predicate);
        break;
      }
    }

    checkArgument(typeDefinition != null, "No class deserialization method found: %s", json);
    return typeDefinition;
  }
}

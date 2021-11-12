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
import java.util.function.BiConsumer;
import java.util.function.Function;

class RequiredDeserializableFieldDefinition<TObject, TField>
    extends RequiredSerializableFieldDefinition<TObject, TField>
    implements DeserializableFieldDefinition<TObject> {

  private final BiConsumer<TObject, TField> setter;
  private final DeserializableTypeDefinition<TField> deserializableType;

  RequiredDeserializableFieldDefinition(
      final String name,
      final Function<TObject, TField> getter,
      final BiConsumer<TObject, TField> setter,
      final DeserializableTypeDefinition<TField> type) {
    super(name, getter, type);
    this.setter = setter;
    this.deserializableType = type;
  }

  @Override
  public void readField(final TObject target, final JsonParser parser) throws IOException {
    final TField value = deserializableType.deserialize(parser);
    setter.accept(target, value);
  }
}

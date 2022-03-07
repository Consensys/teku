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

package tech.pegasys.teku.infrastructure.json.types;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DeserializableArrayTypeDefinition<ItemT, CollectionT extends Iterable<ItemT>>
    extends SerializableArrayTypeDefinition<ItemT, CollectionT>
    implements DeserializableTypeDefinition<CollectionT> {

  private final DeserializableTypeDefinition<ItemT> itemType;
  private final Function<List<ItemT>, CollectionT> createFromList;

  public DeserializableArrayTypeDefinition(
      final DeserializableTypeDefinition<ItemT> itemType,
      final Function<List<ItemT>, CollectionT> createFromList) {
    super(itemType);
    this.itemType = itemType;
    this.createFromList = createFromList;
  }

  @Override
  public CollectionT deserialize(final JsonParser parser) throws IOException {
    if (!parser.isExpectedStartArrayToken()) {
      throw MismatchedInputException.from(
          parser, (Class<?>) null, "Array expected but got " + parser.getCurrentToken());
    }
    final List<ItemT> result = new ArrayList<>();
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      result.add(itemType.deserialize(parser));
    }
    return createFromList.apply(result);
  }
}

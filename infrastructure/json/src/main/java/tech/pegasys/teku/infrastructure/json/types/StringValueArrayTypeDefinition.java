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

import static tech.pegasys.teku.infrastructure.json.JsonUtil.FACTORY;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class StringValueArrayTypeDefinition<ItemT, CollectionT extends Iterable<ItemT>>
    extends SerializableArrayTypeDefinition<ItemT, CollectionT>
    implements StringValueTypeDefinition<CollectionT> {

  private final StringValueTypeDefinition<ItemT> itemType;
  private final Function<List<ItemT>, CollectionT> createFromList;

  public StringValueArrayTypeDefinition(
      final StringValueTypeDefinition<ItemT> itemType,
      final Function<List<ItemT>, CollectionT> createFromList) {
    super(itemType);
    this.itemType = itemType;
    this.createFromList = createFromList;
  }

  public StringValueArrayTypeDefinition(
      final StringValueTypeDefinition<ItemT> itemType,
      final Function<List<ItemT>, CollectionT> createFromList,
      final String description) {
    super(itemType, description);
    this.itemType = itemType;
    this.createFromList = createFromList;
  }

  @Override
  public String serializeToString(final CollectionT values) {
    final StringWriter writer = new StringWriter();
    try (final JsonGenerator gen = FACTORY.createGenerator(writer)) {
      gen.writeStartArray();
      for (ItemT value : values) {
        itemType.serialize(value, gen);
      }
      gen.writeEndArray();
      return writer.toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public CollectionT deserializeFromString(final String value) {
    try (final JsonParser parser = FACTORY.createParser(value)) {
      return deserialize(parser);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public CollectionT deserialize(final JsonParser parser) throws IOException {
    if (!parser.isExpectedStartArrayToken()) {
      throw MismatchedInputException.from(
          parser, String.class, "Array expected but got " + parser.getCurrentToken());
    }
    final List<ItemT> result = new ArrayList<>();
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      result.add(itemType.deserialize(parser));
    }
    return createFromList.apply(result);
  }

  @Override
  public StringValueTypeDefinition<CollectionT> withDescription(final String description) {
    return new StringValueArrayTypeDefinition<>(itemType, createFromList, description);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final StringValueArrayTypeDefinition<?, ?> that =
        (StringValueArrayTypeDefinition<?, ?>) o;
    return Objects.equals(itemType, that.itemType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemType);
  }
}

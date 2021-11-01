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
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public class StringBasedPrimitiveTypeDefinition<T> implements TwoWayTypeDefinition<T> {

  private final Function<String, T> objectFromString;
  private final Function<T, String> stringFromObject;
  private final Optional<String> description;
  private final Optional<String> format;

  public StringBasedPrimitiveTypeDefinition(
      final Function<String, T> objectFromString,
      final Function<T, String> stringFromObject,
      final Optional<String> description,
      final Optional<String> format) {
    this.objectFromString = objectFromString;
    this.stringFromObject = stringFromObject;
    this.description = description;
    this.format = format;
  }

  @Override
  public T deserialize(final JsonParser parser) throws IOException {
    return objectFromString.apply(parser.getValueAsString());
  }

  @Override
  public void serialize(final T value, final JsonGenerator gen) throws IOException {
    gen.writeString(stringFromObject.apply(value));
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("type", "string");
    if (description.isPresent()) {
      gen.writeStringField("description", description.get());
    }
    if (format.isPresent()) {
      gen.writeStringField("format", format.get());
    }
    gen.writeEndObject();
  }
}

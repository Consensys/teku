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

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public class StringBasedPrimitiveTypeDefinition<T> implements DeserializableTypeDefinition<T> {

  private final Function<String, T> objectFromString;
  private final Function<T, String> stringFromObject;
  private final Optional<String> description;
  private final String example;
  private final Optional<String> format;
  private final Optional<String> pattern;

  private StringBasedPrimitiveTypeDefinition(
      final Function<String, T> objectFromString,
      final Function<T, String> stringFromObject,
      final String example,
      final Optional<String> description,
      final Optional<String> format,
      final Optional<String> pattern) {
    this.objectFromString = objectFromString;
    this.stringFromObject = stringFromObject;
    this.example = example;
    this.description = description;
    this.format = format;
    this.pattern = pattern;
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
    gen.writeStringField("example", example);
    if (description.isPresent()) {
      gen.writeStringField("description", description.get());
    }
    if (format.isPresent()) {
      gen.writeStringField("format", format.get());
    }
    if (pattern.isPresent()) {
      gen.writeStringField("pattern", pattern.get());
    }
    gen.writeEndObject();
  }

  public static class StringTypeBuilder<T> {

    private Function<String, T> parser;
    private Function<T, String> formatter;
    private String example;
    private Optional<String> description = Optional.empty();
    private Optional<String> format = Optional.empty();
    private Optional<String> pattern = Optional.empty();

    public StringTypeBuilder<T> parser(final Function<String, T> parser) {
      this.parser = parser;
      return this;
    }

    public StringTypeBuilder<T> formatter(final Function<T, String> formatter) {
      this.formatter = formatter;
      return this;
    }

    public StringTypeBuilder<T> example(final String example) {
      this.example = example;
      return this;
    }

    public StringTypeBuilder<T> description(final String description) {
      this.description = Optional.of(description);
      return this;
    }

    public StringTypeBuilder<T> format(final String format) {
      this.format = Optional.of(format);
      return this;
    }

    public StringTypeBuilder<T> pattern(final String pattern) {
      this.pattern = Optional.of(pattern);
      return this;
    }

    public DeserializableTypeDefinition<T> build() {
      checkNotNull(parser, "Must specify parser");
      checkNotNull(formatter, "Must specify formatter");
      checkNotNull(example, "Must specify example");

      return new StringBasedPrimitiveTypeDefinition<>(
          parser, formatter, example, description, format, pattern);
    }
  }
}

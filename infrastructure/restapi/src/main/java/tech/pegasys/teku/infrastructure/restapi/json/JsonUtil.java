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

package tech.pegasys.teku.infrastructure.restapi.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import tech.pegasys.teku.infrastructure.restapi.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.types.SerializableTypeDefinition;

public class JsonUtil {
  public static final String JSON_CONTENT_TYPE = "application/json";

  public static final JsonFactory FACTORY = new JsonFactory();

  public static <T> String serialize(final T value, final SerializableTypeDefinition<T> type)
      throws JsonProcessingException {
    return serialize(gen -> type.serialize(value, gen));
  }

  public static String serialize(final JsonWriter serializer) throws JsonProcessingException {
    final StringWriter writer = new StringWriter();
    try (final JsonGenerator gen = FACTORY.createGenerator(writer)) {
      serializer.accept(gen);
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return writer.toString();
  }

  public static <T> T parse(final String json, final DeserializableTypeDefinition<T> type)
      throws JsonProcessingException {
    try (final JsonParser parser = FACTORY.createParser(json)) {
      parser.nextToken();
      return type.deserialize(parser);
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public interface JsonWriter {
    void accept(JsonGenerator gen) throws IOException;
  }
}

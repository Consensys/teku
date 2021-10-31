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

package tech.pegasys.teku.infrastructure.restapi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import tech.pegasys.teku.infrastructure.restapi.types.TypeDefinition;

public class MagicSerializerThing {
  private final ObjectMapper objectMapper = new ObjectMapper();

  public <T> String serialize(final TypeDefinition<T> type, final T value) throws IOException {
    final StringWriter writer = new StringWriter();
    final JsonGenerator gen = objectMapper.createGenerator(writer);
    type.getSerializer().serialize(value, gen, objectMapper.getSerializerProvider());
    gen.flush();
    return writer.toString();
  }

  public <T> T deserialize(final TypeDefinition<T> type, final String input) throws IOException {
    final JsonParser parser = objectMapper.createParser(input);
    return type.getDeserializer().deserialize(parser, objectMapper.getDeserializationContext());
  }
}

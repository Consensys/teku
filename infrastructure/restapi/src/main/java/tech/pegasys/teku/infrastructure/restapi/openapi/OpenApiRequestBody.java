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

package tech.pegasys.teku.infrastructure.restapi.openapi;

import static tech.pegasys.teku.infrastructure.restapi.json.JsonUtil.JSON_CONTENT_TYPE;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.restapi.types.DeserializableTypeDefinition;

public class OpenApiRequestBody {
  public static void writeOpenApi(
      final JsonGenerator gen, Optional<DeserializableTypeDefinition<?>> maybeContent)
      throws IOException {
    if (maybeContent.isEmpty()) {
      return;
    }
    final DeserializableTypeDefinition<?> content = maybeContent.get();
    gen.writeObjectFieldStart("requestBody");
    gen.writeObjectFieldStart("content");
    gen.writeObjectFieldStart(JSON_CONTENT_TYPE);
    gen.writeFieldName("schema");
    content.serializeOpenApiTypeOrReference(gen);
    gen.writeEndObject();

    gen.writeEndObject();
    gen.writeEndObject();
  }
}

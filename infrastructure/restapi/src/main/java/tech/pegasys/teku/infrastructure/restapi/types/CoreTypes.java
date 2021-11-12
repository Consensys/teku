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

import java.util.function.Function;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.types.StringBasedPrimitiveTypeDefinition.StringTypeBuilder;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class CoreTypes {
  public static final DeserializableTypeDefinition<String> STRING_TYPE = stringBuilder().build();

  public static final SerializableTypeDefinition<Boolean> BOOLEAN_TYPE =
      new BooleanTypeDefinition();

  public static final DeserializableTypeDefinition<UInt64> UINT64_TYPE =
      DeserializableTypeDefinition.string(UInt64.class)
          .formatter(UInt64::toString)
          .parser(UInt64::valueOf)
          .example("1")
          .description("unsigned 64 bit integer")
          .format("uint64")
          .build();

  public static final DeserializableTypeDefinition<Integer> INTEGER_TYPE =
      new IntegerTypeDefinition();

  public static final SerializableTypeDefinition<HttpErrorResponse> HTTP_ERROR_RESPONSE_TYPE =
      SerializableTypeDefinition.object(HttpErrorResponse.class)
          .name("HttpErrorResponse")
          .withField("status", INTEGER_TYPE, HttpErrorResponse::getStatus)
          .withField("message", STRING_TYPE, HttpErrorResponse::getMessage)
          .build();

  public static DeserializableTypeDefinition<String> string(final String description) {
    return stringBuilder().description(description).build();
  }

  private static StringTypeBuilder<String> stringBuilder() {
    return DeserializableTypeDefinition.string(String.class)
        .formatter(Function.identity())
        .parser(Function.identity());
  }
}

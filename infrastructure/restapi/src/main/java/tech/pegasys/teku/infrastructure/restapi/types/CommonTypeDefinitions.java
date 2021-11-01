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

import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class CommonTypeDefinitions {
  public static final DeserializableTypeDefinition<String> STRING_TYPE =
      new StringBasedPrimitiveTypeDefinition<>(Function.identity(), Function.identity(), "value");

  public static final DeserializableTypeDefinition<UInt64> UINT64_TYPE =
      new StringBasedPrimitiveTypeDefinition<>(
          UInt64::valueOf,
          UInt64::toString,
          "1",
          Optional.of("unsigned 64 bit integer"),
          Optional.of("uint64"));

  public static final DeserializableTypeDefinition<Integer> INTEGER_TYPE =
      new IntegerTypeDefinition();

  public static final SerializableTypeDefinition<HttpErrorResponse> HTTP_ERROR_RESPONSE_TYPE =
      SerializableTypeDefinition.object(HttpErrorResponse.class)
          .withField("status", INTEGER_TYPE, HttpErrorResponse::getStatus)
          .withField("message", STRING_TYPE, HttpErrorResponse::getMessage)
          .build();
}

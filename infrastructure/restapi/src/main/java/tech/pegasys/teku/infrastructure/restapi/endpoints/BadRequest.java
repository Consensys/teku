/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;

public class BadRequest {

  public static final SerializableTypeDefinition<BadRequest> BAD_REQUEST_TYPE =
      SerializableTypeDefinition.object(BadRequest.class)
          .name("BadRequest")
          .withField("code", INTEGER_TYPE, BadRequest::getCode)
          .withField("message", STRING_TYPE, BadRequest::getMessage)
          .build();

  private final Integer code;
  private final String message;

  public BadRequest(Integer code, String message) {
    this.code = code;
    this.message = message;
  }

  public Integer getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}

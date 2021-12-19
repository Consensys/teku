/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.schema;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.provider.JsonProvider;

public class BadRequest {
  private static final Logger LOG = LogManager.getLogger();
  private final Integer code;
  private final String message;

  public BadRequest(String message) {
    this.message = message;
    this.code = SC_BAD_REQUEST;
  }

  @JsonCreator
  public BadRequest(@JsonProperty("code") Integer code, @JsonProperty("message") String message) {
    this.code = code;
    this.message = message;
  }

  @JsonProperty("code")
  public final Integer getCode() {
    return code;
  }

  @JsonProperty("message")
  public final String getMessage() {
    return message;
  }

  public static String serviceUnavailable(final JsonProvider provider)
      throws JsonProcessingException {
    return provider.objectToJSON(new BadRequest(SC_SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE));
  }

  public static String badRequest(final JsonProvider provider, final String message)
      throws JsonProcessingException {
    return provider.objectToJSON(new BadRequest(SC_BAD_REQUEST, message));
  }

  public static String internalError(final JsonProvider provider, final String message)
      throws JsonProcessingException {
    return provider.objectToJSON(new BadRequest(SC_INTERNAL_SERVER_ERROR, message));
  }

  public static String serialize(
      final JsonProvider provider, final Integer status, final String message) {
    try {
      return provider.objectToJSON(new BadRequest(status, message));
    } catch (JsonProcessingException e) {
      LOG.error("Failed to serialize to json", e);
    }
    return "";
  }
}

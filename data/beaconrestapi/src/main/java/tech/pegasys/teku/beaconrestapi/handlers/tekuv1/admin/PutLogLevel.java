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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.INVALID_BODY_SUPPLIED;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_TEKU;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.schema.LogLevel;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;
import tech.pegasys.teku.provider.JsonProvider;

public class PutLogLevel implements Handler {

  public static final String ROUTE = "/teku/v1/admin/log_level";

  private final JsonProvider jsonProvider;

  public PutLogLevel(final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.PUT,
      summary = "Changes the log level without restarting.",
      tags = {TAG_TEKU},
      requestBody =
          @OpenApiRequestBody(
              content = {@OpenApiContent(from = LogLevel.class)},
              description =
                  "```\n{\n  \"level\": (String; acceptable values: ALL, TRACE, DEBUG, INFO, ERROR, FATAL, OFF ),\n"
                      + "  \"log_filter\": [(String; Optional)]\n}\n```"),
      description =
          "Changes the log level without restarting. You can change the log level for all logs, or the log level for specific packages or classes.",
      responses = {
        @OpenApiResponse(
            status = RES_NO_CONTENT,
            description = "The LogLevel was accepted and applied"),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = INVALID_BODY_SUPPLIED),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(final Context ctx) throws Exception {

    try {
      final LogLevel params = jsonProvider.jsonToObject(ctx.body(), LogLevel.class);

      final String[] logFilters = params.getLogFilter().orElseGet(() -> new String[] {""});

      for (final String logFilter : logFilters) {
        LoggingConfigurator.setAllLevels(logFilter, params.getLevel());
      }

      ctx.status(SC_NO_CONTENT);
    } catch (final IllegalArgumentException | JsonMappingException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }
}

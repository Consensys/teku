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

package tech.pegasys.teku.infrastructure.restapi;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;

import io.javalin.core.security.AccessManager;
import io.javalin.core.security.RouteRole;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class AuthorizationManager implements AccessManager {
  private static final Logger LOG = LogManager.getLogger();
  private Optional<String> bearer;
  static final String BEARER_PREFIX = "Bearer ";
  private static String INIT_FAILED =
      "Failed to initialize authorization mapping - will not be able to make requests to api";

  public AuthorizationManager(final Path path) {
    final File passwordFile = path.toFile();
    if (!passwordFile.exists() || !passwordFile.canRead() || passwordFile.isDirectory()) {
      LOG.error(INIT_FAILED);
      bearer = Optional.empty();
      return;
    }
    try (Stream<String> lines = Files.lines(path)) {
      bearer = lines.findFirst().map(val -> URLEncoder.encode(val, StandardCharsets.UTF_8));
    } catch (IOException e) {
      LOG.error(INIT_FAILED, e);
      bearer = Optional.empty();
    }
  }

  private void unauthorized(final Context ctx, final String message) {
    LOG.info(message);
    ctx.json("{\n  \"status\": 401, \n \"message\":\"Unauthorized\"\n}");
    ctx.status(SC_UNAUTHORIZED);
  }

  private void unauthorized(final Context ctx) {
    unauthorized(
        ctx, String.format("API Reject - Unauthorized \"%s %s\"", ctx.method(), ctx.matchedPath()));
  }

  @Override
  public void manage(
      @NotNull final Handler handler,
      @NotNull final Context ctx,
      @NotNull final Set<RouteRole> routeRoles)
      throws Exception {
    if (ctx.matchedPath().equals("/swagger-docs")) {
      handler.handle(ctx);
      return;
    }

    if (bearer.isEmpty() || bearer.get().length() == 0) {
      unauthorized(ctx, "API Reject - no bearer loaded by server.");
      return;
    }

    final String auth = ctx.header("Authorization");
    if (auth == null || !auth.startsWith(BEARER_PREFIX)) {
      unauthorized(ctx, "API Reject - authorization bearer missing from request header");
      return;
    }
    if (!auth.substring(BEARER_PREFIX.length()).equals(bearer.get())) {
      unauthorized(ctx);
      return;
    }

    LOG.info(String.format("API \"%s %s\"", ctx.method(), ctx.matchedPath()));

    handler.handle(ctx);
  }
}

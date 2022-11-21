/*
 * Copyright ConsenSys Software Inc., 2022
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import io.javalin.security.RouteRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AuthorizationManagerTest {
  private static final String PASS = "secure";
  private final Handler handler = mock(Handler.class);
  private final Context context = mock(Context.class);
  private final Set<RouteRole> roles = Set.of();

  @Test
  void shouldNotRequireAuthorizationForSwagger(@TempDir final Path tempDir) throws Exception {
    setupPasswordFile(tempDir);
    when(context.method()).thenReturn(HandlerType.GET);
    when(context.matchedPath()).thenReturn("/swagger-docs");
    AuthorizationManager manager = new AuthorizationManager(tempDir.resolve("passwd"));
    manager.manage(handler, context, roles);
    verify(handler, times(1)).handle(context);
  }

  @Test
  void shouldGrantAccessIfHeaderSet(@TempDir final Path tempDir) throws Exception {
    setupPasswordFile(tempDir);
    when(context.method()).thenReturn(HandlerType.DELETE);
    when(context.matchedPath()).thenReturn("/aPath");
    when(context.header("Authorization")).thenReturn("Bearer " + PASS);
    AuthorizationManager manager = new AuthorizationManager(tempDir.resolve("passwd"));
    manager.manage(handler, context, roles);
    verify(handler, times(1)).handle(context);
  }

  @Test
  void shouldGrantAccessIfHeaderSetEncoded(@TempDir final Path tempDir) throws Exception {
    // url encode will rewrite the ' ' to a '+'
    Files.writeString(tempDir.resolve("passwd"), PASS + " " + PASS);
    when(context.method()).thenReturn(HandlerType.POST);
    when(context.matchedPath()).thenReturn("/aPath");
    when(context.header("Authorization")).thenReturn("Bearer " + PASS + "+" + PASS);
    AuthorizationManager manager = new AuthorizationManager(tempDir.resolve("passwd"));
    manager.manage(handler, context, roles);
    verify(handler, times(1)).handle(context);
  }

  @Test
  void shouldDenyAccessIfHeaderNotSet(@TempDir final Path tempDir) throws Exception {
    setupPasswordFile(tempDir);
    when(context.method()).thenReturn(HandlerType.POST);
    when(context.matchedPath()).thenReturn("/aPath");
    when(context.header("Authorization")).thenReturn(null);
    AuthorizationManager manager = new AuthorizationManager(tempDir.resolve("passwd"));
    manager.manage(handler, context, roles);
    verify(handler, times(0)).handle(context);
    verify(context).status(401);
    verify(context).json(any());
  }

  @Test
  void shouldDenyAccessIfServerBearerMissing(@TempDir final Path tempDir) throws Exception {
    when(context.method()).thenReturn(HandlerType.GET);
    when(context.matchedPath()).thenReturn("/aPath");
    when(context.header("Authorization")).thenReturn(null);
    AuthorizationManager manager = new AuthorizationManager(tempDir.resolve("passwd"));
    manager.manage(handler, context, roles);
    verify(handler, times(0)).handle(context);
    verify(context).status(401);
    verify(context).json(any());
  }

  @Test
  void shouldDenyAccessIfServerBearerEmpty(@TempDir final Path tempDir) throws Exception {
    Files.createFile(tempDir.resolve("passwd"));
    when(context.method()).thenReturn(HandlerType.GET);
    when(context.matchedPath()).thenReturn("/aPath");
    when(context.header("Authorization")).thenReturn(null);
    AuthorizationManager manager = new AuthorizationManager(tempDir.resolve("passwd"));
    manager.manage(handler, context, roles);
    verify(handler, times(0)).handle(context);
    verify(context).status(401);
    verify(context).json(any());
  }

  @Test
  void shouldDenyAccessIfHeaderIsNotBearer(@TempDir final Path tempDir) throws Exception {
    setupPasswordFile(tempDir);
    when(context.method()).thenReturn(HandlerType.GET);
    when(context.matchedPath()).thenReturn("/aPath");
    when(context.header("Authorization")).thenReturn(PASS);
    AuthorizationManager manager = new AuthorizationManager(tempDir.resolve("passwd"));
    manager.manage(handler, context, roles);
    verify(handler, times(0)).handle(context);
    verify(context).status(401);
    verify(context).json(any());
  }

  @Test
  void shouldDenyAccessIfBearerIsIncorrect(@TempDir final Path tempDir) throws Exception {
    setupPasswordFile(tempDir);
    when(context.method()).thenReturn(HandlerType.GET);
    when(context.matchedPath()).thenReturn("/aPath");
    when(context.header("Authorization")).thenReturn("Bearer no");
    AuthorizationManager manager = new AuthorizationManager(tempDir.resolve("passwd"));
    manager.manage(handler, context, roles);
    verify(handler, times(0)).handle(context);
    verify(context).status(401);
    verify(context).json(any());
  }

  private void setupPasswordFile(final Path tempDir) throws IOException {
    Files.writeString(tempDir.resolve("passwd"), PASS);
  }
}

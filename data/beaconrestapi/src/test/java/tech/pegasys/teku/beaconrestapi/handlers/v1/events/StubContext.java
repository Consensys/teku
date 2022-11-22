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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpStatus;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StubContext implements Context {

  private final HttpServletRequest req;
  private final HttpServletResponse res;

  public StubContext(final HttpServletRequest req, final HttpServletResponse res) {
    this.req = req;
    this.res = res;
  }

  @Override
  @SuppressWarnings("TypeParameterUnusedInFormals")
  public <T> T appAttribute(@NotNull final String s) {
    return null;
  }

  @NotNull
  @Override
  public String endpointHandlerPath() {
    return null;
  }

  @Override
  public void future(@NotNull final Supplier<? extends CompletableFuture<?>> supplier) {}

  @NotNull
  @Override
  public HandlerType handlerType() {
    return null;
  }

  @NotNull
  @Override
  public String matchedPath() {
    return null;
  }

  @NotNull
  @Override
  public ServletOutputStream outputStream() {
    return null;
  }

  @NotNull
  @Override
  public String pathParam(@NotNull final String s) {
    return null;
  }

  @NotNull
  @Override
  public Map<String, String> pathParamMap() {
    return null;
  }

  @Override
  public void redirect(@NotNull final String s, @NotNull final HttpStatus httpStatus) {}

  @NotNull
  @Override
  public HttpServletRequest req() {
    return req;
  }

  @NotNull
  @Override
  public HttpServletResponse res() {
    return res;
  }

  @NotNull
  @Override
  public Context result(@NotNull final InputStream inputStream) {
    return null;
  }

  @Nullable
  @Override
  public InputStream resultInputStream() {
    return null;
  }
}

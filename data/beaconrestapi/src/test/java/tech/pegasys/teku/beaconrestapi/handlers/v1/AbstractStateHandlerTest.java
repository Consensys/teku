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

package tech.pegasys.teku.beaconrestapi.handlers.v1;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.Root;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

public class AbstractStateHandlerTest extends AbstractBeaconHandlerTest {

  Function<Bytes32, SafeFuture<Optional<Bytes32>>> defaultRootHandler =
      (root) -> SafeFuture.completedFuture(Optional.of(Bytes32.ZERO));
  Function<UInt64, SafeFuture<Optional<Bytes32>>> defaultSlotHandler =
      (root) -> SafeFuture.completedFuture(Optional.of(Bytes32.ZERO));

  @Test
  public void shouldReturnBadRequestWhenStateIdIsNotProvided() throws Exception {
    final AbstractHandler handler = createHandler(Optional.empty(), Optional.empty());
    handler.handle(context);
    verifyStatusCode(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnUnavailableWhenStoreNotAvailable() throws Exception {
    final AbstractHandler handler = createHandler(Optional.empty(), Optional.empty());
    doThrow(new ChainDataUnavailableException()).when(chainDataProvider).requireStoreAvailable();

    handler.handle(context);
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  public void shouldReturnNotFoundWhenStateIdCanNotBeFound() throws Exception {
    final AbstractHandler handler = createHandler(Optional.empty(), Optional.empty());
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "ead"));

    handler.handle(context);
    verifyStatusCode(SC_NOT_FOUND);
  }

  @Test
  public void shouldDealCorrectlyWithTheExceptionOccurringInTheSlotHandler() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    when(chainDataProvider.stateParameterToSlot("head")).thenReturn(Optional.of(UInt64.ONE));
    final AbstractHandler handler =
        createHandler(
            Optional.empty(),
            Optional.of(
                (__) -> {
                  throw new ChainDataUnavailableException();
                }));

    handler.handle(context);
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  public void shouldDealCorrectlyWithTheExceptionOccurringInTheRootHandler() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "0xdeadbeef"));
    final AbstractHandler handler =
        createHandler(
            Optional.of(
                (__) -> {
                  throw new ChainDataUnavailableException();
                }),
            Optional.empty());

    handler.handle(context);
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }

  private AbstractHandler createHandler(
      Optional<Function<Bytes32, SafeFuture<Optional<Bytes32>>>> maybeRootHandler,
      Optional<Function<UInt64, SafeFuture<Optional<Bytes32>>>> maybeSlotHandler) {
    return new AbstractHandler(jsonProvider) {

      final ResultProcessor<Bytes32> resultProcessor =
          (ctx, root) -> Optional.of(jsonProvider.objectToJSON(new Root(root)));

      @Override
      public void handle(@NotNull Context ctx) throws Exception {
        processStateEndpointRequest(
            chainDataProvider,
            ctx,
            maybeRootHandler.orElse(defaultRootHandler),
            maybeSlotHandler.orElse(defaultSlotHandler),
            resultProcessor);
      }
    };
  }
}

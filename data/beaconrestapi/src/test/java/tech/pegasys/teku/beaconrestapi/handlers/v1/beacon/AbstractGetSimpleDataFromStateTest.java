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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.AssertionsForClassTypes;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;

public class AbstractGetSimpleDataFromStateTest extends AbstractBeaconHandlerTest {
  private static final String ROUTE = "/test/:state_id";
  private static final SerializableTypeDefinition<StateAndMetaData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(StateAndMetaData.class)
          .name("ResponseObject")
          .withField(
              "data",
              SerializableTypeDefinition.object(Bytes32.class)
                  .withField("root", BYTES32_TYPE, Function.identity())
                  .build(),
              stateAndMetaData -> stateAndMetaData.getData().hashTreeRoot())
          .build();

  private final TestHandler handler = new TestHandler(chainDataProvider);

  @Test
  void shouldReturnNotFound() throws JsonProcessingException {
    when(chainDataProvider.getBeaconStateAndMetadata(eq("head")))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    final RestApiRequest request = new RestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);

    AssertionsForClassTypes.assertThat(getResultString())
        .isEqualTo("{\"code\":404,\"message\":\"Not found\"}");
    verify(context, never()).status(any());
  }

  @Test
  public void shouldThrowBadRequest() throws JsonProcessingException {
    when(chainDataProvider.getBeaconStateAndMetadata(eq("invalid")))
        .thenThrow(new BadRequestException("invalid state"));
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "invalid"));
    final RestApiRequest request = new RestApiRequest(context, handler.getMetadata());

    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("invalid state");
  }

  static class TestHandler extends AbstractGetSimpleDataFromState {

    public TestHandler(final ChainDataProvider chainDataProvider) {
      super(
          EndpointMetadata.get(ROUTE)
              .operationId("testFn")
              .summary("test function")
              .description("TEST")
              .pathParam(PARAMETER_STATE_ID)
              .response(SC_OK, "Request successful", RESPONSE_TYPE)
              .response(SC_NOT_FOUND, "Not found", HTTP_ERROR_RESPONSE_TYPE)
              .build(),
          chainDataProvider);
    }

    @Override
    public void handle(@NotNull final Context ctx) throws Exception {
      adapt(ctx);
    }
  }
}

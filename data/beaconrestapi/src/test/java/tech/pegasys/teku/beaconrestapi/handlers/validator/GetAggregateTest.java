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

package tech.pegasys.teku.beaconrestapi.handlers.validator;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;

class GetAggregateTest {

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final Context context = mock(Context.class);
  private final ValidatorDataProvider provider = mock(ValidatorDataProvider.class);
  private final JsonProvider jsonProvider = new JsonProvider();

  private GetAggregate handler;

  @BeforeEach
  public void beforeEach() {
    handler = new GetAggregate(provider, jsonProvider);
  }

  @Test
  public void shouldReturnBadRequestWhenNoParameters() throws Exception {
    when(context.queryParamMap()).thenReturn(emptyMap());

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenMissingAttestationDataRoot() throws Exception {
    final Map<String, List<String>> queryParamsMissingDataRoot =
        Map.of(
            "attestation_data_root", emptyList(),
            "slot", List.of("1"));
    when(context.queryParamMap()).thenReturn(queryParamsMissingDataRoot);

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenMissingSlot() throws Exception {
    final Bytes32 attestationHashTreeRoot = dataStructureUtil.randomAttestation().hash_tree_root();
    final Map<String, List<String>> queryParamsMissingSlot =
        Map.of(
            "attestation_data_root", List.of(attestationHashTreeRoot.toHexString()),
            "slot", emptyList());
    when(context.queryParamMap()).thenReturn(queryParamsMissingSlot);

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnNotFoundWhenAttestationIsNotPresent() throws Exception {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    mockContextWithAttestationParams(attestation);
    when(provider.createAggregate(
            eq(attestation.getData().getSlot()), eq(attestation.hash_tree_root())))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handle(context);
    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnAttestationWhenProviderSuccessfullyCreatesAggregation() throws Exception {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    mockContextWithAttestationParams(attestation);
    final tech.pegasys.teku.api.schema.Attestation schemaAttestation =
        new tech.pegasys.teku.api.schema.Attestation(attestation);
    when(provider.createAggregate(
            eq(attestation.getData().getSlot()), eq(attestation.hash_tree_root())))
        .thenReturn(SafeFuture.completedFuture(Optional.of(schemaAttestation)));

    handler.handle(context);

    verify(context).status(SC_OK);
    verify(context).result(args.capture());

    SafeFuture<String> future = args.getValue();
    String data = future.get();
    assertThat(data).isEqualTo(jsonProvider.objectToJSON(schemaAttestation));
  }

  @Test
  public void shouldReturnBadRequestWhenFutureExceptionIsIllegalArgument() throws Exception {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    mockContextWithAttestationParams(attestation);
    when(provider.createAggregate(
            eq(attestation.getData().getSlot()), eq(attestation.hash_tree_root())))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException()));

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnServerErrorWhenFutureHasUnmappedException() throws Exception {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    mockContextWithAttestationParams(attestation);
    when(provider.createAggregate(
            eq(attestation.getData().getSlot()), eq(attestation.hash_tree_root())))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException()));

    handler.handle(context);
    verify(context).status(SC_INTERNAL_SERVER_ERROR);
  }

  private void mockContextWithAttestationParams(final Attestation attestation) {
    final Map<String, List<String>> validQueryParams =
        Map.of(
            "attestation_data_root", List.of(attestation.hash_tree_root().toHexString()),
            "slot", List.of("1"));
    when(context.queryParamMap()).thenReturn(validQueryParams);
  }
}

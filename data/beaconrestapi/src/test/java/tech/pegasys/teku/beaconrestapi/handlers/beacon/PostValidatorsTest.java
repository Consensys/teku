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

package tech.pegasys.teku.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BeaconValidators;
import tech.pegasys.teku.api.schema.ValidatorsRequest;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.util.config.Constants;

public class PostValidatorsTest {
  private static final String EMPTY_LIST = "[]";

  private final Context context = mock(Context.class);
  private final ChainDataProvider provider = mock(ChainDataProvider.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final BLSPubKey pubKey = new BLSPubKey(new DataStructureUtil().randomPublicKey());
  private final ValidatorsRequest smallRequest =
      new ValidatorsRequest(UInt64.ZERO, List.of(pubKey));

  private PostValidators handler;

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  @BeforeEach
  public void setup() {
    handler = new PostValidators(provider, jsonProvider);
  }

  @Test
  void shouldHandleMissingFinalizedData() throws Exception {
    when(provider.getValidatorsByValidatorsRequest(any()))
        .thenReturn(completedFuture(Optional.empty()));
    when(context.body()).thenReturn(jsonProvider.objectToJSON(smallRequest));
    when(provider.isFinalizedEpoch(smallRequest.epoch)).thenReturn(true);
    handler.handle(context);

    verify(context).status(SC_GONE);
  }

  @Test
  void shouldHandleMissingNonFinalData() throws Exception {
    when(provider.getValidatorsByValidatorsRequest(any()))
        .thenReturn(completedFuture(Optional.empty()));
    when(context.body()).thenReturn(jsonProvider.objectToJSON(smallRequest));
    when(provider.isFinalizedEpoch(smallRequest.epoch)).thenReturn(false);
    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  void shouldReturnBadRequestIfBadObject() throws Exception {
    when(context.body()).thenReturn("{}");
    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
    verify(context).body();
    verifyNoMoreInteractions(context);
  }

  @Test
  void shouldReturnBadRequestWhenEpochTooHigh() throws Exception {
    final String body = "{\"epoch\":" + Constants.FAR_FUTURE_EPOCH + ",\"pubkeys\":[]}";
    when(context.body()).thenReturn(body);
    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
    verify(context).body();
    verifyNoMoreInteractions(context);
  }

  @Test
  public void shouldReturnEmptyListWhenNoValidatorsRequested() throws Exception {
    final String body = "{\"epoch\":0,\"pubkeys\":[]}";

    when(context.body()).thenReturn(body);
    when(provider.getValidatorsByValidatorsRequest(any()))
        .thenReturn(completedFuture(Optional.of(new BeaconValidators())));
    handler.handle(context);

    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    SafeFuture<String> data = args.getValue();
    assertThat(data.get()).isEqualTo(EMPTY_LIST);
  }

  @Test
  public void shouldGetErrorMessageWhenBadPublicKey() throws Exception {
    final String body =
        "{\"epoch\":0,\"pubkeys\":[\"0x1b66ac1fb663c9bc59509846d6ec05345bd908eda73e670af888da41af178bb2305b26a285fa2737f175668d0dff91cc\"]}";

    when(context.body()).thenReturn(body);
    when(provider.getValidatorsByValidatorsRequest(any()))
        .thenReturn(completedFuture(Optional.of(new BeaconValidators())));
    handler.handle(context);

    verify(context).body();
    verify(context).status(SC_BAD_REQUEST);

    final ArgumentCaptor<String> stringArgs = ArgumentCaptor.forClass(String.class);
    verify(context).result(stringArgs.capture());
    String data = stringArgs.getValue();
    assertThat(data).contains("Public key is not valid");
    // index of key that was bad
    assertThat(data).contains("[0]");
  }
}

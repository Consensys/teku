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

package tech.pegasys.artemis.beaconrestapi.handlers.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;

public class PostValidatorDutiesTest {
  private Context context = mock(Context.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final ChainDataProvider provider = mock(ChainDataProvider.class);
  private String EMPTY_LIST = "[]";

  @SuppressWarnings("unchecked")
  final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  @Test
  public void shouldReturnNoContentWhenNoBlockRoot() throws Exception {
    PostValidatorDuties handler = new PostValidatorDuties(provider, jsonProvider);
    handler.handle(context);
    verify(context).status(SC_NO_CONTENT);
  }

  @Test
  public void shouldReturnBadRequestWhenNoEpochNumberInBody() throws Exception {
    PostValidatorDuties handler = new PostValidatorDuties(provider, jsonProvider);
    when(provider.isStoreAvailable()).thenReturn(true);
    when(context.body()).thenReturn("{\"epoch\":\"bob\"}");
    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
    verify(provider).isStoreAvailable();
  }

  @Test
  public void shouldReturnBadRequestWhenNegativeEpochNumberInBody() throws Exception {
    PostValidatorDuties handler = new PostValidatorDuties(provider, jsonProvider);
    when(provider.isStoreAvailable()).thenReturn(true);
    when(context.body()).thenReturn("{\"epoch\":\"-1\"}");
    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
    verify(provider).isStoreAvailable();
  }

  @Test
  public void shouldReturnEmptyListWhenNoValidatorDuties() throws Exception {
    final String body = "{\"epoch\":0,\"pubkeys\":[]}";

    PostValidatorDuties handler = new PostValidatorDuties(provider, jsonProvider);
    when(provider.isStoreAvailable()).thenReturn(true);
    when(context.body()).thenReturn(body);
    when(provider.getValidatorDutiesByRequest(any()))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    handler.handle(context);
    verify(provider).isStoreAvailable();

    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), EMPTY_LIST);
  }
}

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

package tech.pegasys.artemis.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.google.common.primitives.UnsignedLong;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.BLSPubKey;
import tech.pegasys.artemis.api.schema.BeaconValidators;
import tech.pegasys.artemis.api.schema.ValidatorsRequest;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;

public class PostValidatorsTest {
  private static final String EMPTY_LIST = "[]";

  private final Context context = mock(Context.class);;
  private final ChainDataProvider provider = mock(ChainDataProvider.class);;
  private final JsonProvider jsonProvider = new JsonProvider();
  private final BLSPubKey pubKey = new BLSPubKey(new DataStructureUtil().randomPublicKey());
  private final ValidatorsRequest smallRequest =
      new ValidatorsRequest(UnsignedLong.ZERO, List.of(pubKey));

  private PostValidators handler;

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  @BeforeEach
  public void setup() {
    handler = new PostValidators(provider, jsonProvider);
  }

  @Test
  void shouldHandleMissingData() throws Exception {
    when(provider.getValidatorsByValidatorsRequest(any()))
        .thenReturn(completedFuture(Optional.empty()));
    when(context.body()).thenReturn(jsonProvider.objectToJSON(smallRequest));
    handler.handle(context);

    verify(context).status(SC_GONE);
  }

  @Test
  void shouldReturnBadRequestIfBadObject() throws Exception {
    when(context.body()).thenReturn("{}");
    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
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
}

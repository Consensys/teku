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
import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;

import com.google.common.primitives.UnsignedLong;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.api.ValidatorDataProvider;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public class PostDutiesTest {
  private static final List<BLSKeyPair> keyPairs = BLSKeyGenerator.generateKeyPairs(1);
  private static final List<String> pubKeys =
      keyPairs.stream()
          .map(BLSKeyPair::getPublicKey)
          .map(BLSPublicKey::toBytes)
          .map(Bytes::toHexString)
          .collect(Collectors.toList());
  private Context context = mock(Context.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final ValidatorDataProvider provider = mock(ValidatorDataProvider.class);

  @SuppressWarnings("unchecked")
  final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  @Test
  public void shouldReturnBadRequestWhenNoEpochNumberInBody() throws Exception {
    PostDuties handler = new PostDuties(provider, jsonProvider);
    when(provider.isStoreAvailable()).thenReturn(true);
    when(context.body()).thenReturn("{\"epoch\":\"bob\"}");
    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenNegativeEpochNumberInBody() throws Exception {
    PostDuties handler = new PostDuties(provider, jsonProvider);
    when(provider.isStoreAvailable()).thenReturn(true);
    when(context.body()).thenReturn("{\"epoch\":\"-1\"}");
    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldHandleMissingResultForFinalizedEpoch() throws Exception {
    final UnsignedLong epoch = UnsignedLong.ZERO;
    final String body =
        String.format("{\"epoch\":%s, \"pubkeys\":[\"%s\"]}", epoch, pubKeys.get(0));

    PostDuties handler = new PostDuties(provider, jsonProvider);
    when(provider.isStoreAvailable()).thenReturn(true);
    when(context.body()).thenReturn(body);
    when(provider.isEpochFinalized(epoch)).thenReturn(true);
    when(provider.getValidatorDutiesByRequest(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    handler.handle(context);

    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).status(SC_GONE);
    SafeFuture<String> data = args.getValue();
    assertThat(data.get()).isNull();
  }

  @Test
  public void shouldHandleMissingResultForNonFinalizedEpoch() throws Exception {
    final UnsignedLong epoch = UnsignedLong.ZERO;
    final String body =
        String.format("{\"epoch\":%s, \"pubkeys\":[\"%s\"]}", epoch, pubKeys.get(0));

    PostDuties handler = new PostDuties(provider, jsonProvider);
    when(provider.isStoreAvailable()).thenReturn(true);
    when(context.body()).thenReturn(body);
    when(provider.isEpochFinalized(epoch)).thenReturn(false);
    when(provider.getValidatorDutiesByRequest(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    handler.handle(context);

    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).status(SC_NOT_FOUND);
    SafeFuture<String> data = args.getValue();
    assertThat(data.get()).isNull();
  }

  @Test
  public void shouldReturnEmptyListWhenNoValidatorDuties() throws Exception {
    final String body = "{\"epoch\":0,\"pubkeys\":[]}";

    PostDuties handler = new PostDuties(provider, jsonProvider);
    when(provider.isStoreAvailable()).thenReturn(true);
    when(context.body()).thenReturn(body);
    when(provider.getValidatorDutiesByRequest(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Collections.emptyList())));
    handler.handle(context);

    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    SafeFuture<String> data = args.getValue();
    assertThat(data.get()).isEqualTo("[]");
  }
}

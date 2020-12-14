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

package tech.pegasys.teku.beaconrestapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.sync.events.SyncingStatus;

public abstract class AbstractBeaconHandlerTest {

  protected final Eth2Network eth2Network = mock(Eth2Network.class);

  protected final Context context = mock(Context.class);
  protected final JsonProvider jsonProvider = new JsonProvider();
  protected final NetworkDataProvider network = new NetworkDataProvider(eth2Network);

  protected final SyncService syncService = mock(SyncService.class);
  protected final SyncDataProvider syncDataProvider = new SyncDataProvider(syncService);
  private final ArgumentCaptor<String> stringArgs = ArgumentCaptor.forClass(String.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  protected final ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);
  protected final ValidatorDataProvider validatorDataProvider = mock(ValidatorDataProvider.class);

  protected void verifyCacheStatus(final String cacheControlString) {
    verify(context).header(Header.CACHE_CONTROL, cacheControlString);
  }

  protected void verifyStatusCode(final int statusCode) {
    verify(context).status(statusCode);
  }

  protected <T> T getResponseObject(Class<T> clazz) throws JsonProcessingException {
    verify(context).result(stringArgs.capture());
    String val = stringArgs.getValue();
    return jsonProvider.jsonToObject(val, clazz);
  }

  protected <T> T getResponseFromFuture(Class<T> clazz) throws JsonProcessingException {
    verify(context).result(args.capture());
    SafeFuture<String> future = args.getValue();
    assertThat(future).isCompleted();
    String data = future.join();
    return jsonProvider.jsonToObject(data, clazz);
  }

  protected BadRequest getBadRequestFromFuture() throws JsonProcessingException {
    verify(context).result(args.capture());
    SafeFuture<String> future = args.getValue();
    assertThat(future).isCompleted();
    String data = future.join();
    return jsonProvider.jsonToObject(data, BadRequest.class);
  }

  protected SyncingStatus getSyncStatus(
      final boolean isSyncing,
      final long startSlot,
      final long currentSlot,
      final long highestSlot) {
    return new SyncingStatus(
        isSyncing,
        UInt64.valueOf(currentSlot),
        UInt64.valueOf(startSlot),
        UInt64.valueOf(highestSlot));
  }
}

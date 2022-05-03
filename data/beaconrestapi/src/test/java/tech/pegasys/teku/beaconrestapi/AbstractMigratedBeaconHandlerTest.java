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

package tech.pegasys.teku.beaconrestapi;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import io.javalin.http.Context;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.AssertionsForClassTypes;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public abstract class AbstractMigratedBeaconHandlerTest {
  protected final Eth2P2PNetwork eth2P2PNetwork = mock(Eth2P2PNetwork.class);
  protected Spec spec = TestSpecFactory.createMinimalPhase0();

  protected final Context context = mock(Context.class);
  protected final JsonProvider jsonProvider = new JsonProvider();
  protected final NetworkDataProvider network = new NetworkDataProvider(eth2P2PNetwork);

  protected final SyncService syncService = mock(SyncService.class);
  protected final SyncDataProvider syncDataProvider = new SyncDataProvider(syncService);
  protected final SchemaDefinitionCache schemaDefinitionCache = new SchemaDefinitionCache(spec);
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<SafeFuture<ByteArrayInputStream>> futureArgs =
      ArgumentCaptor.forClass(SafeFuture.class);

  private final ArgumentCaptor<byte[]> args = ArgumentCaptor.forClass(byte[].class);

  protected ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);
  protected final ValidatorDataProvider validatorDataProvider = mock(ValidatorDataProvider.class);

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

  protected String getFutureResultString() throws ExecutionException, InterruptedException {
    verify(context).future(futureArgs.capture());
    SafeFuture<ByteArrayInputStream> future = futureArgs.getValue();
    AssertionsForClassTypes.assertThat(future).isCompleted();
    return new String(future.get().readAllBytes(), StandardCharsets.UTF_8);
  }

  protected String getResultString() {
    verify(context).result(args.capture());
    return new String(args.getValue(), StandardCharsets.UTF_8);
  }

  protected SafeFuture<ByteArrayInputStream> getResultFuture() {
    verify(context).future(futureArgs.capture());
    return futureArgs.getValue();
  }

  protected String getResultStringFromSuccessfulFuture() {
    final SafeFuture<ByteArrayInputStream> resultFuture = getResultFuture();
    assertThat(resultFuture).isCompleted();
    final ByteArrayInputStream byteArrayInputStream = safeJoin(getResultFuture());
    return new String(byteArrayInputStream.readAllBytes(), UTF_8);
  }
}

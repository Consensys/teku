/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.artemis.beaconrestapi.BeaconRestApi;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class GenesisTimeHandlerTest {
  private final Context context = Mockito.mock(Context.class);

  private final ChainStorageClient storageClient =
      ChainStorageClient.memoryOnlyClient(new EventBus());

  @Test
  public void shouldReturnEmptyObjectWhenGenesisTimeIsNotSet() {
    BeaconRestApi.getInstance(null, null, 12345);
    //    when(context.status(any(Integer.class))).thenReturn(context);

    //    GenesisTimeHandler.handleRequest(context);
    //
    //    Mockito.verify(context).result("not found");
  }

  @Test
  public void shouldReturnGenesisTimeWhenSet() {
    final UnsignedLong genesisTime = UnsignedLong.valueOf(51234);
    storageClient.setGenesisTime(genesisTime);
    BeaconRestApi.getInstance(storageClient, null, 12345);

    GenesisTimeHandler.handleRequest(context);
    assertThat(context.resultString()).isEqualTo(genesisTime.toString());

    Mockito.verify(context).result(JsonProvider.objectToJSON(genesisTime));
  }
}

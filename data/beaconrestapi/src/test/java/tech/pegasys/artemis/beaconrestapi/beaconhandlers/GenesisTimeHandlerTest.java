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

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class GenesisTimeHandlerTest {
  private Context mockContext = mock(Context.class);
  private final UnsignedLong genesisTime = UnsignedLong.valueOf(51234);
  JsonProvider jsonProvider = new JsonProvider();

  private final ChainStorageClient storageClient =
      ChainStorageClient.memoryOnlyClient(new EventBus());

  @Test
  public void shouldReturnNoContentWhenGenesisTimeIsNotSet() throws Exception {
    GenesisTimeHandler handler = new GenesisTimeHandler(null, jsonProvider);
    handler.handle(mockContext);

    verify(mockContext).status(SC_NO_CONTENT);
  }

  @Test
  public void shouldReturnGenesisTimeWhenSet() throws Exception {
    storageClient.setGenesisTime(genesisTime);
    GenesisTimeHandler handler = new GenesisTimeHandler(storageClient, jsonProvider);
    handler.handle(mockContext);

    verify(mockContext).result(jsonProvider.objectToJSON(genesisTime));
  }
}

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

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler.RequestParams;
import tech.pegasys.artemis.storage.ChainStorageClient;

class GenesisTimeHandlerTest {
  private final RequestParams requestParams = Mockito.mock(RequestParams.class);
  private final ChainStorageClient storageClient = new ChainStorageClient(new EventBus());
  private final GenesisTimeHandler handler = new GenesisTimeHandler(storageClient);

  @Test
  public void shouldReturnEmptyObjectWhenGenesisTimeIsNotSet() {
    assertThat(handler.handleRequest(requestParams)).isEqualTo(emptyMap());
  }

  @Test
  public void shouldReturnGenesisTimeWhenSet() {
    final UnsignedLong genesisTime = UnsignedLong.valueOf(51234);
    storageClient.setGenesisTime(genesisTime);
    assertThat(handler.handleRequest(requestParams))
        .isEqualTo(singletonMap("genesis_time", genesisTime));
  }
}

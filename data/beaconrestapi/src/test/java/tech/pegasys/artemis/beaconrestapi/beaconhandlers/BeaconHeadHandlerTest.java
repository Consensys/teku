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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;

import com.google.common.primitives.UnsignedLong;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.BeaconHead;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;

@ExtendWith(MockitoExtension.class)
public class BeaconHeadHandlerTest {
  @Mock private ChainDataProvider provider;
  @Mock private Context context;
  private final JsonProvider jsonProvider = new JsonProvider();
  private BeaconState rootState = DataStructureUtil.randomBeaconState(1);
  private final UnsignedLong bestSlot = UnsignedLong.valueOf(51234);

  @Test
  public void shouldReturnBeaconHead() throws Exception {
    BeaconHeadHandler handler = new BeaconHeadHandler(provider, jsonProvider);
    Bytes32 blockRoot = Bytes32.random();
    BeaconHead head = new BeaconHead(bestSlot, blockRoot, rootState.hash_tree_root());
    when(provider.getBeaconHead()).thenReturn(Optional.of(head));

    handler.handle(context);

    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).result(jsonProvider.objectToJSON(head));
  }

  @Test
  public void shouldReturnNoContentIfBlockRootNotSet() throws Exception {
    BeaconHeadHandler handler = new BeaconHeadHandler(provider, jsonProvider);
    when(provider.getBeaconHead()).thenReturn(Optional.empty());
    handler.handle(context);

    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).status(SC_NO_CONTENT);
  }
}

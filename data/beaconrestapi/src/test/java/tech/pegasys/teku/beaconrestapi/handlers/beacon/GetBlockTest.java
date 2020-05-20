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

import static com.google.common.primitives.UnsignedLong.ONE;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.teku.beaconrestapi.handlers.beacon.GetBlock.NO_PARAMETERS;
import static tech.pegasys.teku.beaconrestapi.handlers.beacon.GetBlock.NO_VALID_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.beacon.GetBlock.TOO_MANY_PARAMETERS;
import static tech.pegasys.teku.util.async.SafeFuture.completedFuture;

import com.google.common.primitives.UnsignedLong;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.response.GetBlockResponse;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.util.async.SafeFuture;

public class GetBlockTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  private final Context context = mock(Context.class);
  private final ChainDataProvider provider = mock(ChainDataProvider.class);

  private final JsonProvider jsonProvider = new JsonProvider();
  private GetBlock handler;
  private Bytes32 blockRoot = Bytes32.random();
  private tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock signedBeaconBlock =
      dataStructureUtil.randomSignedBeaconBlock(1);

  @BeforeEach
  public void setup() {
    handler = new GetBlock(provider, jsonProvider);
  }

  private void badRequestParamsTest(final Map<String, List<String>> params, String message)
      throws Exception {
    when(context.queryParamMap()).thenReturn(params);

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);

    if (StringUtils.isNotEmpty(message)) {
      BadRequest badRequest = new BadRequest(message);
      verify(context).result(jsonProvider.objectToJSON(badRequest));
    }
  }

  @Test
  public void shouldReturnBadRequestWhenNoParameters() throws Exception {
    badRequestParamsTest(Map.of(), NO_PARAMETERS);
  }

  @Test
  public void shouldReturnBadRequestWhenTooManyParameters() throws Exception {
    final Map<String, List<String>> params =
        Map.of(ROOT, List.of(blockRoot.toHexString()), EPOCH, List.of());
    badRequestParamsTest(params, TOO_MANY_PARAMETERS);
  }

  @Test
  public void shouldReturnBadRequestWhenParameterNotHandled() throws Exception {
    final Map<String, List<String>> params = Map.of("foo", List.of());
    badRequestParamsTest(params, NO_VALID_PARAMETER);
  }

  @Test
  public void shouldReturnBlockWhenQueryByRoot() throws Exception {
    final Map<String, List<String>> params = Map.of(ROOT, List.of(blockRoot.toHexString()));
    SafeFuture<Optional<GetBlockResponse>> providerData =
        completedFuture(Optional.of(new GetBlockResponse(signedBeaconBlock)));
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getBlockByBlockRoot(blockRoot)).thenReturn(providerData);

    handler.handle(context);
    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    SafeFuture<String> future = args.getValue();
    String data = future.get();
    assertThat(data).isEqualTo(jsonProvider.objectToJSON(providerData.get().get()));
  }

  @Test
  public void shouldReturnEmptyWhenQueryByRootNotFound() throws Exception {
    final Map<String, List<String>> params = Map.of(ROOT, List.of(blockRoot.toHexString()));
    SafeFuture<Optional<GetBlockResponse>> providerData = completedFuture(Optional.empty());
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getBlockByBlockRoot(blockRoot)).thenReturn(providerData);

    handler.handle(context);
    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnBlockWhenQueryBySlot() throws Exception {
    final Map<String, List<String>> params = Map.of(SLOT, List.of(ONE.toString()));
    SafeFuture<Optional<GetBlockResponse>> providerData =
        completedFuture(Optional.of(new GetBlockResponse(signedBeaconBlock)));
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getBlockBySlot(ONE)).thenReturn(providerData);

    handler.handle(context);
    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    SafeFuture<String> future = args.getValue();
    String data = future.get();
    assertThat(data).isEqualTo(jsonProvider.objectToJSON(providerData.get().get()));
  }

  @Test
  public void shouldReturnEmptyWhenQueryBySlotNotFound() throws Exception {
    final Map<String, List<String>> params = Map.of(SLOT, List.of(ONE.toString()));
    SafeFuture<Optional<GetBlockResponse>> providerData = completedFuture(Optional.empty());
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getBlockBySlot(ONE)).thenReturn(providerData);

    handler.handle(context);
    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnBlockWhenQueryByEpoch() throws Exception {
    final Map<String, List<String>> params = Map.of(EPOCH, List.of(ONE.toString()));
    SafeFuture<Optional<GetBlockResponse>> providerData =
        completedFuture(Optional.of(new GetBlockResponse(signedBeaconBlock)));
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getBlockBySlot(UnsignedLong.valueOf(8))).thenReturn(providerData);

    handler.handle(context);
    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    SafeFuture<String> future = args.getValue();
    String data = future.get();
    assertThat(data).isEqualTo(jsonProvider.objectToJSON(providerData.get().get()));
  }

  @Test
  public void shouldReturnEmptyWhenQueryByEpochNotFound() throws Exception {
    final Map<String, List<String>> params = Map.of(EPOCH, List.of(ONE.toString()));
    SafeFuture<Optional<GetBlockResponse>> providerData = completedFuture(Optional.empty());
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getBlockBySlot(UnsignedLong.valueOf(8))).thenReturn(providerData);

    handler.handle(context);
    verify(context).status(SC_NOT_FOUND);
  }
}

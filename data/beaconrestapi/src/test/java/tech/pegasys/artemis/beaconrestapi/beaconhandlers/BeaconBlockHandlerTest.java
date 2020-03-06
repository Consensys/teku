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

import static com.google.common.primitives.UnsignedLong.ONE;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.CACHE_NONE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiUtils.INVALID_BYTES32_DATA;
import static tech.pegasys.artemis.beaconrestapi.RestApiUtils.INVALID_NUMERIC_VALUE;
import static tech.pegasys.artemis.beaconrestapi.RestApiUtils.MUST_SPECIFY_ONLY_ONCE;
import static tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconBlockHandler.NO_PARAMETERS;
import static tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconBlockHandler.NO_VALID_PARAMETER;
import static tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconBlockHandler.TOO_MANY_PARAMETERS;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.SignedBeaconBlock;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;

@ExtendWith(MockitoExtension.class)
public class BeaconBlockHandlerTest {
  @Captor private ArgumentCaptor<SafeFuture<String>> args;
  @Mock private Context context;
  @Mock private ChainDataProvider provider;

  private final JsonProvider jsonProvider = new JsonProvider();
  private BeaconBlockHandler handler;
  private Bytes32 blockRoot = Bytes32.random();
  private tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock signedBeaconBlock =
      DataStructureUtil.randomSignedBeaconBlock(1, 1);

  @BeforeEach
  public void setup() {
    handler = new BeaconBlockHandler(provider, jsonProvider);
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
  public void shouldReturnBadRequestWhenRootNotParsable() throws Exception {
    final Map<String, List<String>> params = Map.of(ROOT, List.of("foo"));
    badRequestParamsTest(params, INVALID_BYTES32_DATA);
  }

  @Test
  public void shouldReturnBadRequestWhenEpochNotParsable() throws Exception {
    final Map<String, List<String>> params = Map.of(EPOCH, List.of("foo"));
    badRequestParamsTest(params, INVALID_NUMERIC_VALUE);
  }

  @Test
  public void shouldReturnBadRequestWhenSlotNotParsable() throws Exception {
    final Map<String, List<String>> params = Map.of(SLOT, List.of("foo"));
    badRequestParamsTest(params, INVALID_NUMERIC_VALUE);
  }

  @Test
  public void shouldReturnBadRequestWhenMultipleRootValues() throws Exception {
    final Map<String, List<String>> params = Map.of(ROOT, List.of("1", "2"));
    badRequestParamsTest(params, String.format(MUST_SPECIFY_ONLY_ONCE, ROOT));
  }

  @Test
  public void shouldReturnBadRequestWhenMultipleSlotValues() throws Exception {
    final Map<String, List<String>> params = Map.of(SLOT, List.of("1", "2"));
    badRequestParamsTest(params, String.format(MUST_SPECIFY_ONLY_ONCE, SLOT));
  }

  @Test
  public void shouldReturnBadRequestWhenMultipleEpochValues() throws Exception {
    final Map<String, List<String>> params = Map.of(EPOCH, List.of("1", "2"));
    badRequestParamsTest(params, String.format(MUST_SPECIFY_ONLY_ONCE, EPOCH));
  }

  @Test
  public void shouldReturnBadRequestWhenZeroEpochValues() throws Exception {
    final Map<String, List<String>> params = Map.of(EPOCH, List.of());
    badRequestParamsTest(params, String.format(MUST_SPECIFY_ONLY_ONCE, EPOCH));
  }

  @Test
  public void shouldReturnBlockWhenQueryByRoot() throws Exception {
    final Map<String, List<String>> params = Map.of(ROOT, List.of(blockRoot.toHexString()));
    SafeFuture<Optional<SignedBeaconBlock>> providerData =
        completedFuture(Optional.of(new SignedBeaconBlock(signedBeaconBlock)));
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getBlockByBlockRoot(blockRoot)).thenReturn(providerData);

    handler.handle(context);
    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    SafeFuture<String> future = args.getValue();
    String data = future.get();
    assertThat(data).isEqualTo(jsonProvider.objectToJSON(new SignedBeaconBlock(signedBeaconBlock)));
  }

  @Test
  public void shouldReturnEmptyWhenQueryByRootNotFound() throws Exception {
    final Map<String, List<String>> params = Map.of(ROOT, List.of(blockRoot.toHexString()));
    SafeFuture<Optional<SignedBeaconBlock>> providerData = completedFuture(Optional.empty());
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getBlockByBlockRoot(blockRoot)).thenReturn(providerData);

    handler.handle(context);
    verify(context).status(SC_NOT_FOUND);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
  }

  @Test
  public void shouldReturnBlockWhenQueryBySlot() throws Exception {
    final Map<String, List<String>> params = Map.of(SLOT, List.of(ONE.toString()));
    SafeFuture<Optional<SignedBeaconBlock>> providerData =
        completedFuture(Optional.of(new SignedBeaconBlock(signedBeaconBlock)));
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getBlockBySlot(ONE)).thenReturn(providerData);

    handler.handle(context);
    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    SafeFuture<String> future = args.getValue();
    String data = future.get();
    assertThat(data).isEqualTo(jsonProvider.objectToJSON(new SignedBeaconBlock(signedBeaconBlock)));
  }

  @Test
  public void shouldReturnEmptyWhenQueryBySlotNotFound() throws Exception {
    final Map<String, List<String>> params = Map.of(SLOT, List.of(ONE.toString()));
    SafeFuture<Optional<SignedBeaconBlock>> providerData = completedFuture(Optional.empty());
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getBlockBySlot(ONE)).thenReturn(providerData);

    handler.handle(context);
    verify(context).status(SC_NOT_FOUND);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
  }

  @Test
  public void shouldReturnBlockWhenQueryByEpoch() throws Exception {
    final Map<String, List<String>> params = Map.of(EPOCH, List.of(ONE.toString()));
    SafeFuture<Optional<SignedBeaconBlock>> providerData =
        completedFuture(Optional.of(new SignedBeaconBlock(signedBeaconBlock)));
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getBlockBySlot(UnsignedLong.valueOf(8))).thenReturn(providerData);

    handler.handle(context);
    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    SafeFuture<String> future = args.getValue();
    String data = future.get();
    assertThat(data).isEqualTo(jsonProvider.objectToJSON(new SignedBeaconBlock(signedBeaconBlock)));
  }

  @Test
  public void shouldReturnEmptyWhenQueryByEpochNotFound() throws Exception {
    final Map<String, List<String>> params = Map.of(EPOCH, List.of(ONE.toString()));
    SafeFuture<Optional<SignedBeaconBlock>> providerData = completedFuture(Optional.empty());
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getBlockBySlot(UnsignedLong.valueOf(8))).thenReturn(providerData);

    handler.handle(context);
    verify(context).status(SC_NOT_FOUND);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
  }
}

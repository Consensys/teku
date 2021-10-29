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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.validator.GetNewBlockResponse;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.phase0.BeaconBlockPhase0;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetNewBlockTest {
  private final tech.pegasys.teku.bls.BLSSignature signatureInternal =
      BLSTestUtil.randomSignature(1234);
  private BLSSignature signature = new BLSSignature(signatureInternal);
  private Context context = mock(Context.class);
  protected final ValidatorDataProvider provider = mock(ValidatorDataProvider.class);
  protected final JsonProvider jsonProvider = new JsonProvider();
  private GetNewBlock handler;
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final Bytes32 graffiti = dataStructureUtil.randomBytes32();

  @SuppressWarnings("unchecked")
  final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  @BeforeEach
  public void setup() {
    handler = new GetNewBlock(provider, jsonProvider);
  }

  @Test
  void shouldRequireThatRandaoRevealIsSet() throws Exception {
    badRequestParamsTest(Map.of(), "'randao_reveal' cannot be null or empty.");
  }

  @Test
  void shouldReturnBlockWithoutGraffiti() throws Exception {
    final Map<String, String> pathParams = Map.of(SLOT, "1");
    final Map<String, List<String>> queryParams =
        Map.of(RANDAO_REVEAL, List.of(signature.toHexString()));
    Optional<BeaconBlock> optionalBeaconBlock =
        Optional.of(
            new BeaconBlockPhase0(
                dataStructureUtil.randomBeaconBlock(dataStructureUtil.randomLong())));
    when(context.queryParamMap()).thenReturn(queryParams);
    when(context.pathParamMap()).thenReturn(pathParams);
    when(provider.getMilestoneAtSlot(UInt64.ONE)).thenReturn(SpecMilestone.PHASE0);
    when(provider.getUnsignedBeaconBlockAtSlot(ONE, signature, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(optionalBeaconBlock));
    handler.handle(context);

    verify(context).future(args.capture());
    SafeFuture<String> result = args.getValue();
    assertThat(result)
        .isCompletedWithValue(
            jsonProvider.objectToJSON(new GetNewBlockResponse(optionalBeaconBlock.get())));
  }

  @Test
  void shouldReturnBlockWithGraffiti() throws Exception {
    final Map<String, List<String>> params =
        Map.of(
            RANDAO_REVEAL,
            List.of(signature.toHexString()),
            RestApiConstants.GRAFFITI,
            List.of(graffiti.toHexString()));
    Optional<BeaconBlock> optionalBeaconBlock =
        Optional.of(
            new BeaconBlockPhase0(
                dataStructureUtil.randomBeaconBlock(dataStructureUtil.randomLong())));
    when(context.queryParamMap()).thenReturn(params);
    when(context.pathParamMap()).thenReturn(Map.of(SLOT, "1"));
    when(provider.getMilestoneAtSlot(UInt64.ONE)).thenReturn(SpecMilestone.PHASE0);
    when(provider.getUnsignedBeaconBlockAtSlot(ONE, signature, Optional.of(graffiti)))
        .thenReturn(SafeFuture.completedFuture(optionalBeaconBlock));
    handler.handle(context);

    verify(context).future(args.capture());
    SafeFuture<String> result = args.getValue();

    assertThat(result)
        .isCompletedWithValue(
            jsonProvider.objectToJSON(new GetNewBlockResponse(optionalBeaconBlock.get())));
  }

  @Test
  void shouldReturnServerErrorWhenRuntimeExceptionReceived() throws Exception {
    final Map<String, List<String>> params =
        Map.of(RANDAO_REVEAL, List.of(signature.toHexString()));
    when(context.queryParamMap()).thenReturn(params);
    when(context.pathParamMap()).thenReturn(Map.of(SLOT, "1"));
    when(provider.getMilestoneAtSlot(UInt64.ONE)).thenReturn(SpecMilestone.PHASE0);
    when(provider.getUnsignedBeaconBlockAtSlot(ONE, signature, Optional.empty()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("TEST")));
    handler.handle(context);

    // Exception should just be propagated up via the future
    verify(context, never()).status(anyInt());
    verify(context)
        .future(
            argThat((ArgumentMatcher<SafeFuture<?>>) CompletableFuture::isCompletedExceptionally));
  }

  @Test
  void shouldReturnBadRequestErrorWhenIllegalArgumentExceptionReceived() throws Exception {

    final Map<String, List<String>> params =
        Map.of(RANDAO_REVEAL, List.of(signature.toHexString()));
    when(context.pathParamMap()).thenReturn(Map.of(SLOT, "1"));
    when(context.queryParamMap()).thenReturn(params);
    when(provider.getMilestoneAtSlot(UInt64.ONE)).thenReturn(SpecMilestone.PHASE0);
    when(provider.getUnsignedBeaconBlockAtSlot(ONE, signature, Optional.empty()))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("TEST")));
    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  private void badRequestParamsTest(final Map<String, List<String>> params, String message)
      throws Exception {
    when(context.queryParamMap()).thenReturn(params);
    when(context.pathParamMap()).thenReturn(Map.of(SLOT, "1"));

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);

    if (StringUtils.isNotEmpty(message)) {
      BadRequest badRequest = new BadRequest(message);
      verify(context).json(jsonProvider.objectToJSON(badRequest));
    }
  }
}

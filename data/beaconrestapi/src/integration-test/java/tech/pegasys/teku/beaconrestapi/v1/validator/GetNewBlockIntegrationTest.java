/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beaconrestapi.v1.validator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetNewBlindedBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v2.validator.GetNewBlock;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.validator.coordinator.MissingDepositsException;

public class GetNewBlockIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private DataStructureUtil dataStructureUtil;
  private BLSSignature signature;
  private BeaconBlock randomBlock;

  public static Stream<Arguments> getNewBlockCases() {
    return Stream.of(
        Arguments.of(GetNewBlock.ROUTE.replace("{slot}", "1"), false),
        Arguments.of(GetNewBlindedBlock.ROUTE.replace("{slot}", "1"), true));
  }

  @BeforeEach
  void setup() {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    dataStructureUtil = new DataStructureUtil(spec);
    randomBlock = dataStructureUtil.randomBeaconBlock(UInt64.ONE);
    signature = randomBlock.getBody().getRandaoReveal();
  }

  @ParameterizedTest(name = "blinded_{1}")
  @MethodSource("getNewBlockCases")
  void shouldGetUnsignedBlock_asJson(final String route, final boolean isBlindedBlock)
      throws IOException {
    when(validatorApiChannel.createUnsignedBlock(
            eq(UInt64.ONE), eq(signature), any(), eq(isBlindedBlock)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(randomBlock)));
    Response response = get(route, signature, ContentTypes.JSON);
    assertThat(response.code()).isEqualTo(SC_OK);
    final String body = response.body().string();
    assertThat(body)
        .isEqualTo(
            Resources.toString(
                Resources.getResource(GetNewBlockIntegrationTest.class, "newBlock.json"), UTF_8));
  }

  @ParameterizedTest(name = "blinded_{1}")
  @MethodSource("getNewBlockCases")
  void shouldGetUnsignedBlock_asOctet(final String route, final boolean isBlindedBlock)
      throws IOException {
    when(validatorApiChannel.createUnsignedBlock(
            eq(UInt64.ONE), eq(signature), any(), eq(isBlindedBlock)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(randomBlock)));
    Response response = get(route, signature, ContentTypes.OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_OK);
    BeaconBlock block = spec.deserializeBeaconBlock(Bytes.of(response.body().bytes()));
    assertThat(block).isEqualTo(randomBlock);
  }

  @ParameterizedTest(name = "blinded_{1}")
  @MethodSource("getNewBlockCases")
  void shouldShowNoContent(final String route, final boolean isBlindedBlock) throws IOException {
    when(validatorApiChannel.createUnsignedBlock(
            eq(UInt64.ONE), eq(signature), any(), eq(isBlindedBlock)))
        .thenReturn(SafeFuture.failedFuture(new ChainDataUnavailableException()));
    Response response = get(route, signature, ContentTypes.OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_NO_CONTENT);
    assertThat(response.body().string()).isEqualTo("");
  }

  @ParameterizedTest(name = "blinded_{1}")
  @MethodSource("getNewBlockCases")
  void shouldShowUnavailable(final String route, final boolean isBlindedBlock) throws IOException {
    when(validatorApiChannel.createUnsignedBlock(
            eq(UInt64.ONE), eq(signature), any(), eq(isBlindedBlock)))
        .thenReturn(SafeFuture.failedFuture(new ServiceUnavailableException()));
    Response response = get(route, signature, ContentTypes.OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(response.body().string())
        .isEqualTo(
            "{\"code\":503,\"message\":\"Beacon node is currently syncing and not serving requests\"}");
  }

  @ParameterizedTest(name = "blinded_{1}")
  @MethodSource("getNewBlockCases")
  void shouldNotStackTraceForMissingDeposits(final String route, final boolean isBlindedBlock)
      throws IOException {
    when(validatorApiChannel.createUnsignedBlock(
            eq(UInt64.ONE), eq(signature), any(), eq(isBlindedBlock)))
        .thenReturn(
            SafeFuture.failedFuture(
                MissingDepositsException.missingRange(UInt64.valueOf(1), UInt64.valueOf(10))));
    Response response = get(route, signature, ContentTypes.OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().string())
        .isEqualTo(
            "{\"code\":500,\"message\":\"Unable to create block because ETH1 deposits are not available. Missing deposits 1 to 10\"}");
  }

  public Response get(final String route, final BLSSignature signature, final String contentType)
      throws IOException {
    return getResponse(route, Map.of("randao_reveal", signature.toString()), contentType);
  }
}

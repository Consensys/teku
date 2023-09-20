/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.beaconrestapi.v3;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import okhttp3.Response;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v3.validator.GetNewBlockV3;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true)
public class GetNewBlockV3IntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;
  private SpecMilestone specMilestone;

  @BeforeEach
  void setup(TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    startRestAPIAtGenesis(specMilestone);
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @TestTemplate
  void shouldGetBeaconBlock_asJson() throws IOException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(BELLATRIX).isLessThanOrEqualTo(CAPELLA);
    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(ONE);
    final BLSSignature signature = beaconBlock.getBlock().getBody().getRandaoReveal();
    when(validatorApiChannel.createUnsignedBlock(eq(UInt64.ONE), eq(signature), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconBlock)));
    when(executionLayerBlockProductionManager.getCachedPayloadResult(UInt64.ONE))
        .thenReturn(
            Optional.of(
                new ExecutionPayloadResult(
                    mock(ExecutionPayloadContext.class),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(SafeFuture.completedFuture(UInt256.valueOf(12345))))));
    Response response = get(signature, ContentTypes.JSON);
    assertThat(response.code()).isEqualTo(SC_OK);
    final String body = response.body().string();
    assertThat(body).isEqualTo(getExpectedBlockAsJson(specMilestone));
  }

  public Response get(final BLSSignature signature, final String contentType) throws IOException {
    return getResponse(
        GetNewBlockV3.ROUTE.replace("{slot}", "1"),
        Map.of("randao_reveal", signature.toString()),
        contentType);
  }

  public String getExpectedBlockAsJson(SpecMilestone specMilestone) throws IOException {
    return Resources.toString(
        Resources.getResource(
            GetNewBlockV3IntegrationTest.class,
            String.format("newBlock%s.json", specMilestone.name())),
        UTF_8);
  }
}

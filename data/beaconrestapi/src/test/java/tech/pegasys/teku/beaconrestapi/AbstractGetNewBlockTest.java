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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequestImpl;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

public abstract class AbstractGetNewBlockTest extends AbstractMigratedBeaconHandlerTest {
  protected final BLSSignature signature = BLSTestUtil.randomSignature(1234);

  protected MigratingEndpointAdapter handler;

  @BeforeEach
  public void setup() {
    handler = getHandler();
    final Map<String, String> pathParams = Map.of(SLOT, "1");
    final Map<String, List<String>> queryParams =
        Map.of(RANDAO_REVEAL, List.of(signature.toBytesCompressed().toHexString()));

    when(context.queryParamMap()).thenReturn(queryParams);
    when(context.pathParamMap()).thenReturn(pathParams);
    when(validatorDataProvider.getMilestoneAtSlot(UInt64.ONE)).thenReturn(SpecMilestone.ALTAIR);
  }

  @Test
  void shouldReturnBlockWithoutGraffiti() throws Exception {
    final BeaconBlock randomBeaconBlock = dataStructureUtil.randomBeaconBlock(ONE);
    when(validatorDataProvider.getUnsignedBeaconBlockAtSlot(
            ONE, signature, Optional.empty(), isBlindedBlocks()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(randomBeaconBlock)));

    RestApiRequest request = new RestApiRequestImpl(context, handler.getMetadata());
    handler.handleRequest(request);

    final ByteArrayInputStream byteArrayInputStream = safeJoin(getResultFuture());
    assertThat(new String(byteArrayInputStream.readAllBytes(), UTF_8))
        .isEqualTo(
            Resources.toString(
                Resources.getResource(AbstractGetNewBlockTest.class, "beaconBlock.json"), UTF_8));
  }

  @Test
  void shouldThrowExceptionWithEmptyBlock() throws Exception {
    when(validatorDataProvider.getUnsignedBeaconBlockAtSlot(
            ONE, signature, Optional.empty(), isBlindedBlocks()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    RestApiRequestImpl request = new RestApiRequestImpl(context, handler.getMetadata());
    handler.handleRequest(request);

    SafeFuture<ByteArrayInputStream> future = getResultFuture();
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasRootCauseInstanceOf(ChainDataUnavailableException.class);
  }

  public abstract MigratingEndpointAdapter getHandler();

  public abstract boolean isBlindedBlocks();
}

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

package tech.pegasys.teku.beaconrestapi;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.google.common.io.Resources;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

public abstract class AbstractGetNewBlockTest extends AbstractMigratedBeaconHandlerTest {
  protected final BLSSignature signature = BLSTestUtil.randomSignature(1234);

  @BeforeEach
  public void setup() {
    setHandler(getHandler());
    request.setPathParameter(SLOT, "1");
    request.setQueryParameter(RANDAO_REVEAL, signature.toBytesCompressed().toHexString());
    when(validatorDataProvider.getMilestoneAtSlot(UInt64.ONE)).thenReturn(SpecMilestone.ALTAIR);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldReturnBlockWithoutGraffiti() throws Exception {
    final BeaconBlock randomBeaconBlock = dataStructureUtil.randomBeaconBlock(ONE);
    doReturn(SafeFuture.completedFuture(Optional.of(randomBeaconBlock)))
        .when(validatorDataProvider)
        .getUnsignedBeaconBlockAtSlot(ONE, signature, Optional.empty(), isBlindedBlocks());

    handler.handleRequest(request);

    assertThat(request.getResponseBody()).isEqualTo(randomBeaconBlock);
    // Check block serializes correctly
    assertThat(request.getResponseBodyAsJson(handler))
        .isEqualTo(
            Resources.toString(
                Resources.getResource(AbstractGetNewBlockTest.class, "beaconBlock.json"), UTF_8));
  }

  @Test
  void shouldThrowExceptionWithEmptyBlock() throws Exception {

    doReturn(SafeFuture.completedFuture(Optional.empty()))
        .when(validatorDataProvider)
        .getUnsignedBeaconBlockAtSlot(ONE, signature, Optional.empty(), isBlindedBlocks());

    handler.handleRequest(request);
    assertThat(request.getResponseError()).containsInstanceOf(ChainDataUnavailableException.class);
  }

  public abstract RestApiEndpoint getHandler();

  public abstract boolean isBlindedBlocks();
}

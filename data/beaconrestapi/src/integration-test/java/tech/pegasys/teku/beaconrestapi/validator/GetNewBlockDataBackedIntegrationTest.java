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

package tech.pegasys.teku.beaconrestapi.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.teku.util.async.SafeFuture.completedFuture;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.validator.GetNewBlock;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.util.async.SafeFuture;

public class GetNewBlockDataBackedIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private final UnsignedLong SIX_HUNDRED = UnsignedLong.valueOf(600L);
  private final tech.pegasys.teku.bls.BLSSignature signatureInternal =
      tech.pegasys.teku.bls.BLSSignature.random(1234);
  private BLSSignature signature = new BLSSignature(signatureInternal);

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  void shouldProduceBlockForNextSlot() throws Exception {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    createBlocksAtSlotsAndMapToApiResult(SEVEN);
    BeaconBlock block = dataStructureUtil.randomBeaconBlock(EIGHT);
    SafeFuture<Optional<BeaconBlock>> futureBlock = completedFuture(Optional.of(block));

    when(validatorApiChannel.createUnsignedBlock(any(), any(), any())).thenReturn(futureBlock);
    Response response = getUnsignedBlock(EIGHT);
    verify(validatorApiChannel).createUnsignedBlock(eq(EIGHT), any(), any());
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  void shouldNotProduceBlockForFarFutureSlot() throws Exception {
    createBlocksAtSlotsAndMapToApiResult(SIX);
    Response response = getUnsignedBlock(SIX_HUNDRED);
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    BadRequest badRequest = jsonProvider.jsonToObject(response.body().string(), BadRequest.class);
    assertThat(badRequest.getMessage())
        .isEqualTo(ValidatorDataProvider.CANNOT_PRODUCE_FAR_FUTURE_BLOCK);
  }

  @Test
  void shouldNotProduceBlockForHistoricSlot() throws Exception {
    createBlocksAtSlotsAndMapToApiResult(SEVEN);
    Response response = getUnsignedBlock(SIX);
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    BadRequest badRequest = jsonProvider.jsonToObject(response.body().string(), BadRequest.class);
    assertThat(badRequest.getMessage())
        .isEqualTo(ValidatorDataProvider.CANNOT_PRODUCE_HISTORIC_BLOCK);
  }

  private Response getUnsignedBlock(final UnsignedLong slot) throws IOException {
    return getResponse(
        GetNewBlock.ROUTE, Map.of(SLOT, slot.toString(), RANDAO_REVEAL, signature.toHexString()));
  }
}

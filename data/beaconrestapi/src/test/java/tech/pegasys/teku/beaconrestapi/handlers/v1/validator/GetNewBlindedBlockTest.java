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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

public class GetNewBlindedBlockTest extends AbstractMigratedBeaconHandlerTest {
  private final BLSSignature signatureInternal = BLSTestUtil.randomSignature(1234);
  private GetNewBlindedBlock handler;

  @BeforeEach
  public void setup() {
    handler = new GetNewBlindedBlock(validatorDataProvider, schemaDefinition);
    final Map<String, String> pathParams = Map.of(SLOT, "1");
    final Map<String, List<String>> queryParams =
        Map.of(RANDAO_REVEAL, List.of(signatureInternal.toBytesCompressed().toHexString()));

    when(context.queryParamMap()).thenReturn(queryParams);
    when(context.pathParamMap()).thenReturn(pathParams);
    when(validatorDataProvider.getMilestoneAtSlot(UInt64.ONE)).thenReturn(SpecMilestone.ALTAIR);
  }

  @Test
  void shouldThrowExceptionWithEmptyBlock() throws Exception {
    when(validatorDataProvider.getUnsignedBeaconBlockAtSlot(
            ONE, signatureInternal, Optional.empty(), true))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    RestApiRequest request = new RestApiRequest(context, handler.getMetadata());
    handler.handleRequest(request);

    SafeFuture<String> future = getResultFuture();
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasRootCauseInstanceOf(ChainDataUnavailableException.class);
  }

  protected SafeFuture<String> getResultFuture() {
    verify(context).future(args.capture());
    return args.getValue();
  }
}

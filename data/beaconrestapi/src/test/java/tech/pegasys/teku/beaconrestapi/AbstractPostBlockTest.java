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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.schema.phase0.BeaconBlockPhase0;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

public abstract class AbstractPostBlockTest extends AbstractMigratedBeaconHandlerTest {

  public abstract RestApiEndpoint getHandler();

  public abstract boolean isBlinded();

  protected void setupDeneb() {
    spec = TestSpecFactory.createMinimalDeneb();
    schemaDefinitionCache = new SchemaDefinitionCache(spec);
    dataStructureUtil = new DataStructureUtil(spec);
    setup();
  }

  @BeforeEach
  public void setup() {
    setHandler(getHandler());
  }

  @Test
  void shouldReturnUnavailableIfSyncing() throws Exception {
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void shouldReturnBadRequestIfArgumentNotJSON() {
    assertThatThrownBy(
            () ->
                handler
                    .getMetadata()
                    .getRequestBody(
                        new ByteArrayInputStream("Not a beacon block".getBytes(UTF_8)),
                        Optional.empty()))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void shouldReturnBadRequestIfArgumentNotSignedBeaconBlock() throws Exception {
    final String notASignedBlock =
        jsonProvider.objectToJSON(new BeaconBlockPhase0(dataStructureUtil.randomBeaconBlock(3)));

    assertThatThrownBy(
            () ->
                handler
                    .getMetadata()
                    .getRequestBody(
                        new ByteArrayInputStream(notASignedBlock.getBytes(UTF_8)),
                        Optional.empty()))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void shouldReturnOkIfBlockImportSuccessful() throws Exception {
    final SendSignedBlockResult successResult =
        SendSignedBlockResult.success(dataStructureUtil.randomBytes32());

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    request.setRequestBody(getRandomSignedBeaconBlock());
    setupValidatorDataProviderSubmit(SafeFuture.completedFuture(successResult));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReturnAcceptedIfBlockFailsValidation() throws Exception {
    final SendSignedBlockResult failResult = SendSignedBlockResult.notImported("Invalid block");
    final SafeFuture<SendSignedBlockResult> validatorBlockResultSafeFuture =
        SafeFuture.completedFuture(failResult);

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    request.setRequestBody(getRandomSignedBeaconBlock());

    setupValidatorDataProviderSubmit(validatorBlockResultSafeFuture);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_ACCEPTED);
    assertThat(request.getResponseBody()).isNull();
  }

  private void setupValidatorDataProviderSubmit(final SafeFuture<SendSignedBlockResult> future) {
    if (isBlinded()) {
      when(validatorDataProvider.submitSignedBlindedBlock(any())).thenReturn(future);
    } else {
      when(validatorDataProvider.submitSignedBlock((SignedBeaconBlock) any())).thenReturn(future);
    }
  }

  private SignedBeaconBlock getRandomSignedBeaconBlock() {
    if (isBlinded()) {
      return dataStructureUtil.randomSignedBlindedBeaconBlock(3);
    } else {
      return dataStructureUtil.randomSignedBeaconBlock(3);
    }
  }
}

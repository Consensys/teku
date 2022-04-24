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

import static javax.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.schema.phase0.BeaconBlockPhase0;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

public abstract class AbstractPostBlockTest extends AbstractMigratedBeaconHandlerTest {
  protected MigratingEndpointAdapter handler;

  public abstract MigratingEndpointAdapter getHandler();

  public abstract boolean isBlinded();

  @BeforeEach
  public void setup() {
    handler = getHandler();
  }

  @Test
  void shouldReturnUnavailableIfSyncing() throws Exception {
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);

    handler.handle(context);

    verify(context).status(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void shouldReturnBadRequestIfArgumentNotJSON() {
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    withRequestBody("Not a beacon block.");

    assertThatThrownBy(() -> handler.handle(context)).isInstanceOf(BadRequestException.class);
  }

  @Test
  void shouldReturnBadRequestIfArgumentNotSignedBeaconBlock() throws Exception {
    final String notASignedBlock =
        jsonProvider.objectToJSON(new BeaconBlockPhase0(dataStructureUtil.randomBeaconBlock(3)));

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    withRequestBody(notASignedBlock);
    when(validatorDataProvider.parseBlock(any(), any()))
        .thenThrow(mock(JsonProcessingException.class));

    assertThatThrownBy(() -> handler.handle(context)).isInstanceOf(BadRequestException.class);
  }

  @Test
  void shouldReturnOkIfBlockImportSuccessful() throws Exception {
    final SendSignedBlockResult successResult =
        SendSignedBlockResult.success(dataStructureUtil.randomBytes32());

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    withRequestBody(buildSignedBeaconBlock());
    setupValidatorDataProviderSubmit(SafeFuture.completedFuture(successResult));

    handler.handle(context);

    verify(context).status(SC_OK);
    assertThat(getResultStringFromSuccessfulFuture()).isEqualTo("");
  }

  private void setupValidatorDataProviderSubmit(final SafeFuture<SendSignedBlockResult> future) {

    if (isBlinded()) {
      when(validatorDataProvider.submitSignedBlindedBlock(any())).thenReturn(future);
    } else {
      when(validatorDataProvider.submitSignedBlock((SignedBeaconBlock) any())).thenReturn(future);
    }
  }

  @Test
  void shouldReturnAcceptedIfBlockFailsValidation() throws Exception {
    final SendSignedBlockResult failResult = SendSignedBlockResult.notImported("Invalid block");
    final SafeFuture<SendSignedBlockResult> validatorBlockResultSafeFuture =
        SafeFuture.completedFuture(failResult);

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    withRequestBody(buildSignedBeaconBlock());

    setupValidatorDataProviderSubmit(validatorBlockResultSafeFuture);

    handler.handle(context);

    verify(context).status(SC_ACCEPTED);
    assertThat(getResultStringFromSuccessfulFuture()).isEqualTo("");
  }

  private String buildSignedBeaconBlock() throws JsonProcessingException {
    return jsonProvider.objectToJSON(
        tech.pegasys.teku.api.schema.SignedBeaconBlock.create(
            dataStructureUtil.randomSignedBeaconBlock(3)));
  }

  private void withRequestBody(final String data) {
    when(context.bodyAsInputStream())
        .thenReturn(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
  }
}

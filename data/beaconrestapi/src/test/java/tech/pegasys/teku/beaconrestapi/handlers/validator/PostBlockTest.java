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

package tech.pegasys.teku.beaconrestapi.handlers.validator;

import static javax.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.SyncStatus;
import tech.pegasys.teku.api.schema.SyncingStatus;
import tech.pegasys.teku.api.schema.ValidatorBlockResult;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;

class PostBlockTest {

  private final Context context = mock(Context.class);
  private final ValidatorDataProvider validatorDataProvider = mock(ValidatorDataProvider.class);
  private final SyncDataProvider syncDataProvider = mock(SyncDataProvider.class);

  private final JsonProvider jsonProvider = new JsonProvider();
  private final PostBlock handler =
      new PostBlock(validatorDataProvider, syncDataProvider, jsonProvider);

  DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  void shouldReturnUnavailableIfSyncing() throws Exception {
    when(syncDataProvider.getSyncStatus()).thenReturn(buildSyncStatus(true));

    handler.handle(context);

    verify(context).status(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void shouldReturnBadRequestIfArgumentNotJSON() throws Exception {
    when(syncDataProvider.getSyncStatus()).thenReturn(buildSyncStatus(false));
    when(context.body()).thenReturn("Not a beacon block.");

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  void shouldReturnBadRequestIfArgumentNotSignedBeaconBlock() throws Exception {
    final String notASignedBlock =
        jsonProvider.objectToJSON(new BeaconBlock(dataStructureUtil.randomBeaconBlock(3)));

    when(syncDataProvider.getSyncStatus()).thenReturn(buildSyncStatus(false));
    when(context.body()).thenReturn(notASignedBlock);

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  void shouldReturnOkIfBlockImportSuccessful() throws Exception {
    final ValidatorBlockResult successResult =
        new ValidatorBlockResult(
            200, Optional.empty(), Optional.of(dataStructureUtil.randomBytes32()));
    final SafeFuture<ValidatorBlockResult> validatorBlockResultSafeFuture =
        SafeFuture.completedFuture(successResult);

    when(syncDataProvider.getSyncStatus()).thenReturn(buildSyncStatus(false));
    when(context.body()).thenReturn(buildSignedBeaconBlock());
    when(validatorDataProvider.submitSignedBlock(any())).thenReturn(validatorBlockResultSafeFuture);

    handler.handle(context);

    verify(context).status(SC_OK);
  }

  @Test
  void shouldReturnAcceptedIfBlockFailsValidation() throws Exception {
    final ValidatorBlockResult failResult =
        new ValidatorBlockResult(202, Optional.of(new Exception()), Optional.empty());
    final SafeFuture<ValidatorBlockResult> validatorBlockResultSafeFuture =
        SafeFuture.completedFuture(failResult);

    when(syncDataProvider.getSyncStatus()).thenReturn(buildSyncStatus(false));
    when(context.body()).thenReturn(buildSignedBeaconBlock());
    when(validatorDataProvider.submitSignedBlock(any())).thenReturn(validatorBlockResultSafeFuture);

    handler.handle(context);

    verify(context).status(SC_ACCEPTED);
  }

  private SyncingStatus buildSyncStatus(final boolean isSyncing) {
    return new SyncingStatus(
        isSyncing, new SyncStatus(UnsignedLong.ZERO, UnsignedLong.ONE, UnsignedLong.MAX_VALUE));
  }

  private String buildSignedBeaconBlock() throws JsonProcessingException {
    return jsonProvider.objectToJSON(
        new SignedBeaconBlock(dataStructureUtil.randomSignedBeaconBlock(3)));
  }
}

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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_SYNCING_STATUS;

import java.util.Optional;
import java.util.function.Function;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.validator.api.SyncingStatus;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class GetSyncingStatusRequest extends AbstractTypeDefRequest {

  private static final DeserializableTypeDefinition<SyncingStatus>
      SYNCING_STATUS_RESPONSE_TYPE_DEFINITION =
          DeserializableTypeDefinition.object(SyncingStatus.class, SyncingStatus.Builder.class)
              .name("SyncingStatusResponse")
              .initializer(SyncingStatus.Builder::builder)
              .finisher(SyncingStatus.Builder::build)
              .withField(
                  "data",
                  SyncingStatus.TYPE_DEFINITION,
                  Function.identity(),
                  ((builder, syncingStatus) ->
                      builder
                          .headSlot(syncingStatus.getHeadSlot())
                          .syncDistance(syncingStatus.getSyncDistance())
                          .isSyncing(syncingStatus.isSyncing())
                          .isOptimistic(syncingStatus.getIsOptimistic())))
              .build();

  public GetSyncingStatusRequest(final OkHttpClient okHttpClient, final HttpUrl baseEndpoint) {
    super(baseEndpoint, okHttpClient);
  }

  public Optional<SyncingStatus> getSyncingStatus() {
    return get(GET_SYNCING_STATUS, new ResponseHandler<>(SYNCING_STATUS_RESPONSE_TYPE_DEFINITION));
  }
}

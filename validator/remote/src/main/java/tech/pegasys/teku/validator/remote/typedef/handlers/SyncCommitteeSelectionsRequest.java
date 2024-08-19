/*
 * Copyright Consensys Software Inc., 2024
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

import static tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof.SYNC_COMMITTEE_SELECTION_PROOF;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class SyncCommitteeSelectionsRequest extends AbstractTypeDefRequest {

  public SyncCommitteeSelectionsRequest(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
  }

  public Optional<List<SyncCommitteeSelectionProof>> submit(
      final List<SyncCommitteeSelectionProof> validatorsPartialProof) {
    return postJson(
        ValidatorApiMethod.SYNC_COMMITTEE_SELECTIONS,
        Collections.emptyMap(),
        validatorsPartialProof,
        listOf(SYNC_COMMITTEE_SELECTION_PROOF),
        new ResponseHandler<>(
                SharedApiTypes.withDataWrapper(
                    "SyncCommitteeSelectionsResponse", listOf(SYNC_COMMITTEE_SELECTION_PROOF)))
            .withHandler(SC_NOT_IMPLEMENTED, (request, response) -> Optional.empty())
            .withHandler(SC_SERVICE_UNAVAILABLE, (request, response) -> Optional.empty()));
  }
}

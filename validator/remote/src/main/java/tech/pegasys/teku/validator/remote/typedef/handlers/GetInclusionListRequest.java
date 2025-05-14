/*
 * Copyright Consensys Software Inc., 2025
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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.TransactionSchema;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class GetInclusionListRequest extends AbstractTypeDefRequest {

  final Spec spec;

  public GetInclusionListRequest(
      final Spec spec, final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
  }

  public Optional<List<Transaction>> submit(final UInt64 slot) {
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final SpecConfigEip7805 specConfig = spec.getSpecConfig(epoch).toVersionEip7805().orElseThrow();
    return get(
        ValidatorApiMethod.GET_INCLUSION_LIST,
        Map.of(),
        Map.of(SLOT, slot.toString()),
        new ResponseHandler<>(
                SharedApiTypes.withDataWrapper(
                    "GetInclusionListResponse",
                    listOf(new TransactionSchema(specConfig).getJsonTypeDefinition())))
            .withHandler(SC_SERVICE_UNAVAILABLE, (request, response) -> Optional.empty()));
  }
}

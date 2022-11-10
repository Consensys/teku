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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;

/**
 * Get Deposit Snapshot Tree, see <a
 * href="https://github.com/ethereum/EIPs/blob/master/EIPS/eip-4881.md">EIP-4881</a>
 */
public class GetDepositSnapshot extends RestApiEndpoint {

  public static final String ROUTE = "/teku/v1/beacon/deposit_snapshot";

  private static final SerializableTypeDefinition<DepositTreeSnapshot> DEPOSIT_SNAPSHOT_TYPE =
      DepositTreeSnapshot.getJsonTypeDefinition();

  public static final SerializableTypeDefinition<DepositTreeSnapshot>
      DEPOSIT_SNAPSHOT_RESPONSE_TYPE =
          SerializableTypeDefinition.<DepositTreeSnapshot>object()
              .name("GetDepositSnapshotResponse")
              .withField("data", DEPOSIT_SNAPSHOT_TYPE, Function.identity())
              .build();

  private final Eth1DataProvider eth1DataProvider;

  public GetDepositSnapshot(final Eth1DataProvider eth1DataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getDepositSnapshot")
            .summary("Get finalized DepositTreeSnapshot")
            .description(
                "Latest finalized DepositTreeSnapshot that could be used to reconstruct Deposit merkle tree. "
                    + "See EIP-4881 for details.")
            .tags(TAG_TEKU)
            .response(
                SC_OK,
                "Request successful",
                DEPOSIT_SNAPSHOT_RESPONSE_TYPE,
                new OctetStreamResponseContentTypeDefinition<>(
                    SszData::sszSerialize, __ -> Collections.emptyMap()))
            .withNotFoundResponse()
            .build());
    this.eth1DataProvider = eth1DataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Optional<DepositTreeSnapshot> snapshot =
        eth1DataProvider.getFinalizedDepositTreeSnapshot();
    if (snapshot.isEmpty()) {
      request.respondError(SC_NOT_FOUND, "No finalized snapshot available");
    } else {
      request.respondOk(snapshot.get());
    }
  }
}

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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES4_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;

public class GetGenesis extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/genesis";
  private final ChainDataProvider chainDataProvider;

  private static final SerializableTypeDefinition<ResponseData> GENESIS_DATA_TYPE =
      SerializableTypeDefinition.object(ResponseData.class)
          .withField("genesis_time", UINT64_TYPE, ResponseData::getGenesisTime)
          .withField(
              "genesis_validators_root", BYTES32_TYPE, ResponseData::getGenesisValidatorsRoot)
          .withField("genesis_fork_version", BYTES4_TYPE, ResponseData::getGenesisForkVersion)
          .build();

  private static final SerializableTypeDefinition<ResponseData> GENESIS_RESPONSE_TYPE =
      SerializableTypeDefinition.object(ResponseData.class)
          .name("GetGenesisResponse")
          .withField("data", GENESIS_DATA_TYPE, Function.identity())
          .build();

  public GetGenesis(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetGenesis(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getGenesis")
            .summary("Get chain genesis details")
            .description(
                "Retrieve details of the chain's genesis which can be used to identify chain.")
            .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
            .response(SC_OK, "Request successful", GENESIS_RESPONSE_TYPE)
            .response(SC_NOT_FOUND, "Chain genesis info is not yet known")
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  private Optional<GenesisData> getGenesisData() {
    if (!chainDataProvider.isStoreAvailable()) {
      return Optional.empty();
    }
    return Optional.of(chainDataProvider.getGenesisStateData());
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final Optional<GenesisData> maybeData = getGenesisData();
    if (maybeData.isEmpty()) {
      request.respondWithCode(SC_NOT_FOUND);
      return;
    }
    request.respondOk(new ResponseData(maybeData.get(), chainDataProvider.getGenesisForkVersion()));
  }

  static class ResponseData {

    private final UInt64 genesisTime;
    private final Bytes32 genesisValidatorsRoot;
    private final Bytes4 genesisForkVersion;

    ResponseData(
        final UInt64 genesisTime,
        final Bytes32 genesisValidatorsRoot,
        final Bytes4 genesisForkVersion) {
      this.genesisTime = genesisTime;
      this.genesisValidatorsRoot = genesisValidatorsRoot;
      this.genesisForkVersion = genesisForkVersion;
    }

    ResponseData(final GenesisData genesisData, Bytes4 genesisForkVersion) {
      this.genesisTime = genesisData.getGenesisTime();
      this.genesisValidatorsRoot = genesisData.getGenesisValidatorsRoot();
      this.genesisForkVersion = genesisForkVersion;
    }

    public UInt64 getGenesisTime() {
      return genesisTime;
    }

    public Bytes32 getGenesisValidatorsRoot() {
      return genesisValidatorsRoot;
    }

    public Bytes4 getGenesisForkVersion() {
      return genesisForkVersion;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ResponseData that = (ResponseData) o;
      return Objects.equals(genesisTime, that.genesisTime)
          && Objects.equals(genesisValidatorsRoot, that.genesisValidatorsRoot)
          && Objects.equals(genesisForkVersion, that.genesisForkVersion);
    }

    @Override
    public int hashCode() {
      return Objects.hash(genesisTime, genesisValidatorsRoot, genesisForkVersion);
    }
  }
}

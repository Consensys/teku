package tech.pegasys.teku.validator.remote.typedef;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;

import java.util.Optional;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_GENESIS;

public class GetGenesisRequest extends AbstractTypeDefRequest{

  private static final DeserializableTypeDefinition<GenesisData> GENESIS_DATA =
      DeserializableTypeDefinition.object(GenesisData.class, GenesisData.Builder.class)
          .finisher(GenesisData.Builder::build)
          .initializer(GenesisData::builder)
          .withField(
              "genesis_time",
              UINT64_TYPE,
              GenesisData::getGenesisTime,
              GenesisData.Builder::genesisTime)
          .withField(
              "genesis_validators_root",
              BYTES32_TYPE,
              GenesisData::getGenesisValidatorsRoot,
              GenesisData.Builder::genesisValidatorsRoot)
          .build();
  private static final DeserializableTypeDefinition<GetGenesisResponse> GENESIS_RESPONSE =
      DeserializableTypeDefinition.object(GetGenesisResponse.class)
          .name("GetGenesisResponse")
          .initializer(GetGenesisResponse::new)
          .withField("data", GENESIS_DATA,
              GetGenesisResponse::getData,
              GetGenesisResponse::setData
          ).build();


  public GetGenesisRequest(final OkHttpClient okHttpClient, final HttpUrl baseEndpoint) {
    super(baseEndpoint, okHttpClient);
  }

  public Optional<GenesisData> getGenesisData() {
    final Optional<GetGenesisResponse> response =
        get(GET_GENESIS, new ResponseHandler<>(GENESIS_RESPONSE));
    return response.map(GetGenesisResponse::getData);
  }

  public static class GetGenesisResponse {
    private GenesisData data;

    GetGenesisResponse() {}

    public GenesisData getData() {
      return data;
    }

    public void setData(final GenesisData data) {
      this.data = data;
    }
  }
}

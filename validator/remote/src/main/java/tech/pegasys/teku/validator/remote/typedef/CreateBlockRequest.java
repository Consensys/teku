package tech.pegasys.teku.validator.remote.typedef;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_UNSIGNED_BLINDED_BLOCK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_UNSIGNED_BLOCK_V2;

public class CreateBlockRequest extends AbstractTypeDefRequest{
  private static final Logger LOG = LogManager.getLogger();
  private final SpecVersion specVersion;
  private final boolean preferOctetStream;
  private final DeserializableTypeDefinition<GetBlockResponse> unsignedBlockDefinition;
  private final ValidatorApiMethod apiMethod;

  public CreateBlockRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final SpecVersion specVersion,
      final boolean preferOctetStream,
      final boolean blinded) {
    super(baseEndpoint, okHttpClient);
    this.specVersion = specVersion;
    this.preferOctetStream = preferOctetStream;
    apiMethod = blinded ? GET_UNSIGNED_BLINDED_BLOCK : GET_UNSIGNED_BLOCK_V2;

    unsignedBlockDefinition = getUnsignedBlockSchema(blinded ? specVersion.getSchemaDefinitions().getBlindedBeaconBlockSchema().getJsonTypeDefinition() :
        specVersion.getSchemaDefinitions().getBeaconBlockSchema().getJsonTypeDefinition());
  }

  public Optional<BeaconBlock> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti) {
    final Map<String, String> pathParams = Map.of("slot", slot.toString());
    final Map<String, String> queryParams = new HashMap<>();
    final Map<String, String> headers = new HashMap<>();
    queryParams.put("randao_reveal", randaoReveal.toString());
    graffiti.ifPresent(bytes32 -> queryParams.put("graffiti", bytes32.toHexString()));

    if(preferOctetStream) {
      headers.put("Accept", "application/octet-stream");
    }

    return get(
        apiMethod,
        pathParams,
        queryParams,
        headers,
        new ResponseHandler<>(unsignedBlockDefinition).withHandler(SC_OK, this::handleBeaconBlockResult))
        .map(GetBlockResponse::getData);
  }

  private Optional<GetBlockResponse> handleBeaconBlockResult(final Request request, final Response response) {
    try {
      if ("application/octet-stream".equalsIgnoreCase(response.header("Content-Type"))) {
        return Optional.of(new GetBlockResponse(
            specVersion.getSchemaDefinitions()
                .getBeaconBlockSchema()
                .sszDeserialize(Bytes.of(response.body().bytes())))
        );
      }
      return Optional.ofNullable(JsonUtil.parse(response.body().string(), unsignedBlockDefinition));
    } catch (IOException e) {
      LOG.trace("Failed to parse response object creating block");
    }
    return Optional.empty();
  }

  private DeserializableTypeDefinition<GetBlockResponse> getUnsignedBlockSchema(final DeserializableTypeDefinition<BeaconBlock> typeDefinition) {
    return DeserializableTypeDefinition.object(GetBlockResponse.class)
        .initializer(GetBlockResponse::new)
        .withField(
            "data",
            typeDefinition,
            GetBlockResponse::getData,
            GetBlockResponse::setData)
        .withField(
            "version",
            DeserializableTypeDefinition.enumOf(SpecMilestone.class),
            GetBlockResponse::getSpecMilestone,
            GetBlockResponse::setSpecMilestone)
        .build();
  }

  static class GetBlockResponse {
    private BeaconBlock data;
    private SpecMilestone specMilestone;

    public GetBlockResponse() {}

    public GetBlockResponse(final BeaconBlock data) {
      this.data = data;
    }

    public BeaconBlock getData() {
      return data;
    }

    public void setData(final BeaconBlock data) {
      this.data = data;
    }

    public SpecMilestone getSpecMilestone() {
      return specMilestone;
    }

    public void setSpecMilestone(final SpecMilestone specMilestone) {
      this.specMilestone = specMilestone;
    }
  }
}

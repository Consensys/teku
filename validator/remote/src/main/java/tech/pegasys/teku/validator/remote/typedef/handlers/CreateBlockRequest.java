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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_UNSIGNED_BLINDED_BLOCK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_UNSIGNED_BLOCK_V2;

import com.google.common.net.MediaType;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.BlindedBlockEndpointNotAvailableException;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class CreateBlockRequest extends AbstractTypeDefRequest {

  private static final Logger LOG = LogManager.getLogger();

  private final UInt64 slot;
  private final boolean preferSszBlockEncoding;
  private final ValidatorApiMethod apiMethod;
  private final BeaconBlockSchema beaconBlockSchema;
  private final DeserializableTypeDefinition<GetBlockResponse> getBlockResponseDefinition;
  private final ResponseHandler<GetBlockResponse> responseHandler;

  public CreateBlockRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final Spec spec,
      final UInt64 slot,
      final boolean blinded,
      final boolean preferSszBlockEncoding) {
    super(baseEndpoint, okHttpClient);
    this.slot = slot;
    this.preferSszBlockEncoding = preferSszBlockEncoding;
    apiMethod = blinded ? GET_UNSIGNED_BLINDED_BLOCK : GET_UNSIGNED_BLOCK_V2;
    beaconBlockSchema =
        blinded
            ? spec.atSlot(slot).getSchemaDefinitions().getBlindedBeaconBlockSchema()
            : spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockSchema();
    getBlockResponseDefinition =
        DeserializableTypeDefinition.object(GetBlockResponse.class)
            .initializer(GetBlockResponse::new)
            .withField(
                "data",
                beaconBlockSchema.getJsonTypeDefinition(),
                GetBlockResponse::getData,
                GetBlockResponse::setData)
            .withField(
                "version",
                DeserializableTypeDefinition.enumOf(SpecMilestone.class),
                GetBlockResponse::getSpecMilestone,
                GetBlockResponse::setSpecMilestone)
            .build();
    final ResponseHandler<GetBlockResponse> responseHandler =
        new ResponseHandler<>(getBlockResponseDefinition)
            .withHandler(SC_OK, this::handleBeaconBlockResult);
    this.responseHandler =
        blinded
            ? responseHandler.withHandler(
                SC_NOT_FOUND,
                (__, ___) -> {
                  throw new BlindedBlockEndpointNotAvailableException();
                })
            : responseHandler;
  }

  public Optional<BeaconBlock> createUnsignedBlock(
      final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("randao_reveal", randaoReveal.toString());
    final Map<String, String> headers = new HashMap<>();
    graffiti.ifPresent(bytes32 -> queryParams.put("graffiti", bytes32.toHexString()));

    if (preferSszBlockEncoding) {
      // application/octet-stream is preferred, but will accept application/json
      headers.put("Accept", "application/octet-stream;q=0.9, application/json;q=0.4");
    }
    return get(apiMethod, Map.of("slot", slot.toString()), queryParams, headers, responseHandler)
        .map(GetBlockResponse::getData);
  }

  private Optional<GetBlockResponse> handleBeaconBlockResult(
      final Request request, final Response response) {
    try {
      final String responseContentType = response.header("Content-Type");
      if (responseContentType != null
          && MediaType.parse(responseContentType).is(MediaType.OCTET_STREAM)) {
        return Optional.of(
            new GetBlockResponse(
                beaconBlockSchema.sszDeserialize(Bytes.of(response.body().bytes()))));
      }
      return Optional.of(JsonUtil.parse(response.body().string(), getBlockResponseDefinition));
    } catch (IOException e) {
      LOG.trace("Failed to parse response object creating block", e);
    }
    return Optional.empty();
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

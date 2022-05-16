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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_UNSIGNED_BLINDED_BLOCK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_UNSIGNED_BLOCK_V2;

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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockResponse;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class CreateBlockRequest extends AbstractTypeDefRequest {
  private static final Logger LOG = LogManager.getLogger();
  private final UInt64 slot;
  private DeserializableTypeDefinition<GetBlockResponse> getBlockResponseDefinition;
  private final DeserializableTypeDefinition<BeaconBlock> unsignedBlockDefinition;
  private final ValidatorApiMethod apiMethod;

  public CreateBlockRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final SchemaDefinitionCache schemaDefinitionCache,
      final UInt64 slot,
      final boolean blinded) {
    super(baseEndpoint, okHttpClient);
    apiMethod = blinded ? GET_UNSIGNED_BLINDED_BLOCK : GET_UNSIGNED_BLOCK_V2;
    this.slot = slot;
    this.unsignedBlockDefinition =
        blinded
            ? schemaDefinitionCache
                .atSlot(slot)
                .getBlindedBeaconBlockSchema()
                .getJsonTypeDefinition()
            : schemaDefinitionCache.atSlot(slot).getBeaconBlockSchema().getJsonTypeDefinition();
    this.getBlockResponseDefinition =
        DeserializableTypeDefinition.object(GetBlockResponse.class)
            .initializer(GetBlockResponse::new)
            .withField(
                "data",
                unsignedBlockDefinition,
                GetBlockResponse::getData,
                GetBlockResponse::setData)
            .withField(
                "version",
                DeserializableTypeDefinition.enumOf(SpecMilestone.class),
                GetBlockResponse::getSpecMilestone,
                GetBlockResponse::setSpecMilestone)
            .build();
  }

  public Optional<BeaconBlock> createUnsignedBlock(
      final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("randao_reveal", randaoReveal.toString());
    graffiti.ifPresent(bytes32 -> queryParams.put("graffiti", bytes32.toHexString()));

    return get(
            apiMethod,
            Map.of("slot", slot.toString()),
            queryParams,
            new ResponseHandler<>(getBlockResponseDefinition)
                .withHandler(SC_OK, this::handleBeaconBlockResult))
        .map(GetBlockResponse::getData);
  }

  private Optional<GetBlockResponse> handleBeaconBlockResult(
      final Request request, final Response response) {
    try {
      return Optional.of(JsonUtil.parse(response.body().string(), getBlockResponseDefinition));
    } catch (IOException e) {
      LOG.trace("Failed to parse response object creating block");
    }
    return Optional.empty();
  }

  static class GetBlockResponse {
    private BeaconBlock data;
    private SpecMilestone specMilestone;

    public GetBlockResponse() {}

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

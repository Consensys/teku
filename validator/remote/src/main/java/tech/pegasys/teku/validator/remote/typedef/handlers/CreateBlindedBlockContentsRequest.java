/*
 * Copyright ConsenSys Software Inc., 2023
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
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlindedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlindedBlockContentsSchema;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class CreateBlindedBlockContentsRequest extends AbstractTypeDefRequest {

  private static final Logger LOG = LogManager.getLogger();

  private final UInt64 slot;
  private final boolean preferSszBlockContentsEncoding;
  private final BlindedBlockContentsSchema blindedBlockContentsSchema;
  private final DeserializableTypeDefinition<GetBlindedBlockContentsResponse>
      getBlockContentsResponseDefinition;
  private final ResponseHandler<GetBlindedBlockContentsResponse> responseHandler;

  public CreateBlindedBlockContentsRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final Spec spec,
      final UInt64 slot,
      final boolean preferSszBlockContentsEncoding) {
    super(baseEndpoint, okHttpClient);
    this.slot = slot;
    this.preferSszBlockContentsEncoding = preferSszBlockContentsEncoding;
    blindedBlockContentsSchema =
        spec.atSlot(slot)
            .getSchemaDefinitions()
            .toVersionDeneb()
            .orElseThrow()
            .getBlindedBlockContentsSchema();
    getBlockContentsResponseDefinition =
        DeserializableTypeDefinition.object(GetBlindedBlockContentsResponse.class)
            .initializer(GetBlindedBlockContentsResponse::new)
            .withField(
                "data",
                blindedBlockContentsSchema.getJsonTypeDefinition(),
                GetBlindedBlockContentsResponse::getData,
                GetBlindedBlockContentsResponse::setData)
            .withField(
                "version",
                DeserializableTypeDefinition.enumOf(SpecMilestone.class),
                GetBlindedBlockContentsResponse::getSpecMilestone,
                GetBlindedBlockContentsResponse::setSpecMilestone)
            .build();
    final ResponseHandler<GetBlindedBlockContentsResponse> responseHandler =
        new ResponseHandler<>(getBlockContentsResponseDefinition)
            .withHandler(SC_OK, this::handleBlockContentsResult);
    this.responseHandler = responseHandler;
  }

  public Optional<BlindedBlockContents> createUnsignedBlindedBlockContents(
      final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("randao_reveal", randaoReveal.toString());
    final Map<String, String> headers = new HashMap<>();
    graffiti.ifPresent(bytes32 -> queryParams.put("graffiti", bytes32.toHexString()));

    if (preferSszBlockContentsEncoding) {
      // application/octet-stream is preferred, but will accept application/json
      headers.put("Accept", "application/octet-stream;q=0.9, application/json;q=0.4");
    }
    return get(
            GET_UNSIGNED_BLINDED_BLOCK,
            Map.of("slot", slot.toString()),
            queryParams,
            headers,
            responseHandler)
        .map(GetBlindedBlockContentsResponse::getData);
  }

  private Optional<GetBlindedBlockContentsResponse> handleBlockContentsResult(
      final Request request, final Response response) {
    try {
      final String responseContentType = response.header("Content-Type");
      if (responseContentType != null
          && MediaType.parse(responseContentType).is(MediaType.OCTET_STREAM)) {
        return Optional.of(
            new GetBlindedBlockContentsResponse(
                blindedBlockContentsSchema.sszDeserialize(Bytes.of(response.body().bytes()))));
      }
      return Optional.of(
          JsonUtil.parse(response.body().string(), getBlockContentsResponseDefinition));
    } catch (IOException e) {
      LOG.trace("Failed to parse response object creating block contents", e);
    }
    return Optional.empty();
  }

  public static class GetBlindedBlockContentsResponse {
    private BlindedBlockContents data;
    private SpecMilestone specMilestone;

    public GetBlindedBlockContentsResponse() {}

    public GetBlindedBlockContentsResponse(final BlindedBlockContents data) {
      this.data = data;
    }

    public BlindedBlockContents getData() {
      return data;
    }

    public void setData(final BlindedBlockContents data) {
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

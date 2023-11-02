/*
 * Copyright Consensys Software Inc., 2023
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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT256_TYPE;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_UNSIGNED_BLOCK_V3;

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
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class ProduceBlockRequest extends AbstractTypeDefRequest {

  private static final Logger LOG = LogManager.getLogger();

  private final UInt64 slot;
  private final boolean preferSszBlockEncoding;
  private final BlockContainerSchema<BlockContainer> blockContainerSchema;
  private final BlockContainerSchema<BlockContainer> blindedBlockContainerSchema;
  private final ResponseHandler<ProduceBlockResponse> responseHandler;

  public final DeserializableOneOfTypeDefinition<ProduceBlockResponse> produceBlockTypeDefinition;

  public ProduceBlockRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final Spec spec,
      final UInt64 slot,
      final boolean preferSszBlockEncoding) {
    super(baseEndpoint, okHttpClient);
    this.slot = slot;
    this.preferSszBlockEncoding = preferSszBlockEncoding;
    this.blockContainerSchema = spec.atSlot(slot).getSchemaDefinitions().getBlockContainerSchema();
    this.blindedBlockContainerSchema =
        spec.atSlot(slot).getSchemaDefinitions().getBlindedBlockContainerSchema();

    final DeserializableTypeDefinition<ProduceBlockResponse> produceBlockResponseDefinition =
        buildDeserializableTypeDefinition(blockContainerSchema.getJsonTypeDefinition());
    final DeserializableTypeDefinition<ProduceBlockResponse> produceBlindedBlockResponseDefinition =
        buildDeserializableTypeDefinition(blindedBlockContainerSchema.getJsonTypeDefinition());

    this.produceBlockTypeDefinition =
        DeserializableOneOfTypeDefinition.object(ProduceBlockResponse.class)
            .withType(
                x -> true,
                s -> s.matches(".*\"execution_payload_blinded\" *: *false.*"),
                produceBlockResponseDefinition)
            .withType(
                x -> true,
                s -> s.matches(".*\"execution_payload_blinded\" *: *true.*"),
                produceBlindedBlockResponseDefinition)
            .build();

    this.responseHandler =
        new ResponseHandler<>(produceBlockTypeDefinition)
            .withHandler(SC_OK, this::handleBlockContainerResult);
  }

  public Optional<BlockContainer> createUnsignedBlock(
      final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("randao_reveal", randaoReveal.toString());
    final Map<String, String> headers = new HashMap<>();
    graffiti.ifPresent(bytes32 -> queryParams.put("graffiti", bytes32.toHexString()));

    if (this.preferSszBlockEncoding) {
      // application/octet-stream is preferred, but will accept application/json
      headers.put("Accept", "application/octet-stream;q=0.9, application/json;q=0.4");
    }

    return get(
            GET_UNSIGNED_BLOCK_V3,
            Map.of("slot", slot.toString()),
            queryParams,
            headers,
            this.responseHandler)
        .map(ProduceBlockResponse::getData);
  }

  private Optional<ProduceBlockResponse> handleBlockContainerResult(
      final Request request, final Response response) {
    try {
      final String responseContentType = response.header("Content-Type");
      if (responseContentType != null
          && MediaType.parse(responseContentType).is(MediaType.OCTET_STREAM)) {
        if (Boolean.parseBoolean(response.header(HEADER_EXECUTION_PAYLOAD_BLINDED))) {
          return Optional.of(
              new ProduceBlockResponse(
                  this.blindedBlockContainerSchema.sszDeserialize(
                      Bytes.of(response.body().bytes()))));
        } else {
          return Optional.of(
              new ProduceBlockResponse(
                  this.blockContainerSchema.sszDeserialize(Bytes.of(response.body().bytes()))));
        }
      } else {
        return Optional.of(JsonUtil.parse(response.body().string(), produceBlockTypeDefinition));
      }
    } catch (final IOException ex) {
      LOG.trace("Failed to parse response object creating block", ex);
    }
    return Optional.empty();
  }

  private DeserializableTypeDefinition<ProduceBlockResponse> buildDeserializableTypeDefinition(
      final DeserializableTypeDefinition<BlockContainer> jsonTypeDefinition) {
    return DeserializableTypeDefinition.object(ProduceBlockResponse.class)
        .initializer(ProduceBlockResponse::new)
        .withField(
            EXECUTION_PAYLOAD_BLINDED,
            BOOLEAN_TYPE,
            ProduceBlockResponse::getExecutionPayloadBlinded,
            ProduceBlockResponse::setExecutionPayloadBlinded)
        .withField(
            EXECUTION_PAYLOAD_VALUE,
            UINT256_TYPE,
            ProduceBlockResponse::getExecutionPayloadValue,
            ProduceBlockResponse::setExecutionPayloadValue)
        .withField(
            CONSENSUS_BLOCK_VALUE,
            UINT256_TYPE,
            ProduceBlockResponse::getConsensusBlockValue,
            ProduceBlockResponse::setConsensusBlockValue)
        .withField(
            "data",
            jsonTypeDefinition,
            ProduceBlockResponse::getData,
            ProduceBlockResponse::setData)
        .withField(
            "version",
            DeserializableTypeDefinition.enumOf(SpecMilestone.class),
            ProduceBlockResponse::getSpecMilestone,
            ProduceBlockResponse::setSpecMilestone)
        .build();
  }

  static class ProduceBlockResponse {
    private BlockContainer data;
    private Boolean executionPayloadBlinded;
    private UInt256 executionPayloadValue;
    private UInt256 consensusBlockValue;
    private SpecMilestone specMilestone;

    public ProduceBlockResponse() {}

    public ProduceBlockResponse(final BlockContainer data) {
      this.data = data;
    }

    public BlockContainer getData() {
      return data;
    }

    public void setData(final BlockContainer data) {
      this.data = data;
    }

    public Boolean getExecutionPayloadBlinded() {
      return executionPayloadBlinded;
    }

    public void setExecutionPayloadBlinded(Boolean executionPayloadBlinded) {
      this.executionPayloadBlinded = executionPayloadBlinded;
    }

    public UInt256 getConsensusBlockValue() {
      return consensusBlockValue;
    }

    public void setConsensusBlockValue(UInt256 consensusBlockValue) {
      this.consensusBlockValue = consensusBlockValue;
    }

    public UInt256 getExecutionPayloadValue() {
      return executionPayloadValue;
    }

    public void setExecutionPayloadValue(UInt256 executionPayloadValue) {
      this.executionPayloadValue = executionPayloadValue;
    }

    public SpecMilestone getSpecMilestone() {
      return specMilestone;
    }

    public void setSpecMilestone(final SpecMilestone specMilestone) {
      this.specMilestone = specMilestone;
    }
  }
}

/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.builder.rest.handlers;

import static tech.pegasys.teku.builder.rest.BuilderApiMethod.GET_EXECUTION_PAYLOAD_BID;
import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.withDataWrapper;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;

import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.builder.rest.ResponseHandler;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.SignedRequestAuth;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetExecutionPayloadBidRequest extends AbstractBuilderRequest {

  private final Spec spec;

  public GetExecutionPayloadBidRequest(
      final Spec spec, final HttpUrl baseEndpoint, final OkHttpClient httpClient) {
    super(baseEndpoint, httpClient);
    this.spec = spec;
  }

  public Optional<ExecutionPayloadBid> submit(
      final UInt64 slot,
      final Bytes32 parentHash,
      final Bytes32 parentRoot,
      final BLSPublicKey proposerPubkey,
      final Optional<SignedRequestAuth> signedRequestAuth) {

    final Map<String, String> urlParams =
        Map.of(
            "slot", slot.toString(),
            "parent_hash", parentHash.toHexString(),
            "parent_root", parentRoot.toHexString(),
            "proposer_pubkey", proposerPubkey.toString());

    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());

    final ResponseHandler<ExecutionPayloadBid> responseHandler =
        new ResponseHandler<>(withDataWrapper(schemaDefinitions.getExecutionPayloadBidSchema()))
            .withHandler(SC_NO_CONTENT, (req, resp) -> Optional.empty());

    if (signedRequestAuth.isPresent()) {
      return postJson(
          GET_EXECUTION_PAYLOAD_BID,
          urlParams,
          signedRequestAuth.get(),
          ApiSchemas.SIGNED_REQUEST_AUTH_SCHEMA.getJsonTypeDefinition(),
          responseHandler);
    } else {
      return postEmpty(GET_EXECUTION_PAYLOAD_BID, urlParams, responseHandler);
    }
  }
}

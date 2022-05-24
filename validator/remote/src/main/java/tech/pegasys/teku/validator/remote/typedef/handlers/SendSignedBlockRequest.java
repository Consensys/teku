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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNSUPPORTED_MEDIA_TYPE;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_BLINDED_BLOCK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_BLOCK;

import java.util.Collections;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class SendSignedBlockRequest extends AbstractTypeDefRequest {

  private final ResponseHandler<Object> responseHandler =
      new ResponseHandler<>()
          .withHandler(SC_UNSUPPORTED_MEDIA_TYPE, this::handleUnsupportedResponse);

  private final boolean preferSszBlockEncoding;
  private volatile boolean unsupportedMediaType = false;

  public SendSignedBlockRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final boolean preferSszBlockEncoding) {
    super(baseEndpoint, okHttpClient);
    this.preferSszBlockEncoding = preferSszBlockEncoding;
  }

  public SendSignedBlockResult sendSignedBlock(SignedBeaconBlock signedBeaconBlock) {
    final ValidatorApiMethod apiMethod =
        signedBeaconBlock.getMessage().getBody().isBlinded()
            ? SEND_SIGNED_BLINDED_BLOCK
            : SEND_SIGNED_BLOCK;

    return preferSszBlockEncoding && !unsupportedMediaType
        ? sendSignedBlockAsSszOrFallback(signedBeaconBlock, apiMethod)
        : sendSignedBlockAsJson(apiMethod, signedBeaconBlock);
  }

  private SendSignedBlockResult sendSignedBlockAsSszOrFallback(
      final SignedBeaconBlock signedBeaconBlock, final ValidatorApiMethod apiMethod) {
    final SendSignedBlockResult result = sendSignedBlockAsSsz(apiMethod, signedBeaconBlock);
    if (!result.isPublished() && unsupportedMediaType) {
      return sendSignedBlockAsJson(apiMethod, signedBeaconBlock);
    }
    return result;
  }

  private SendSignedBlockResult sendSignedBlockAsSsz(
      final ValidatorApiMethod apiMethod, final SignedBeaconBlock signedBeaconBlock) {
    return postOctetStream(
            apiMethod,
            Collections.emptyMap(),
            signedBeaconBlock.sszSerialize().toArray(),
            responseHandler)
        .map(__ -> SendSignedBlockResult.success(signedBeaconBlock.getRoot()))
        .orElseGet(() -> SendSignedBlockResult.notImported("UNKNOWN"));
  }

  private SendSignedBlockResult sendSignedBlockAsJson(
      final ValidatorApiMethod apiMethod, final SignedBeaconBlock signedBeaconBlock) {
    return postJson(
            apiMethod,
            Collections.emptyMap(),
            signedBeaconBlock,
            signedBeaconBlock.getSchema().getJsonTypeDefinition(),
            responseHandler)
        .map(__ -> SendSignedBlockResult.success(signedBeaconBlock.getRoot()))
        .orElseGet(() -> SendSignedBlockResult.notImported("UNKNOWN"));
  }

  private Optional<Object> handleUnsupportedResponse(
      final Request request, final Response response) {
    unsupportedMediaType = true;
    return Optional.empty();
  }
}

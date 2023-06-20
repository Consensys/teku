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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNSUPPORTED_MEDIA_TYPE;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_BLINDED_BLOCK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_BLOCK;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class SendSignedBlockRequest extends AbstractTypeDefRequest {

  private final ResponseHandler<Object> sszResponseHandler =
      new ResponseHandler<>()
          .withHandler(SC_UNSUPPORTED_MEDIA_TYPE, this::handleUnsupportedSszRequest);

  private final Spec spec;
  private final AtomicBoolean preferSszBlockEncoding;

  public SendSignedBlockRequest(
      final Spec spec,
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final boolean preferSszBlockEncoding) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
    this.preferSszBlockEncoding = new AtomicBoolean(preferSszBlockEncoding);
  }

  public SendSignedBlockResult sendSignedBlock(final SignedBlockContainer signedBlockContainer) {
    final boolean blinded = signedBlockContainer.isBlinded();

    final ValidatorApiMethod apiMethod = blinded ? SEND_SIGNED_BLINDED_BLOCK : SEND_SIGNED_BLOCK;

    final SchemaDefinitions schemaDefinitions =
        spec.atSlot(signedBlockContainer.getSlot()).getSchemaDefinitions();

    final DeserializableTypeDefinition<SignedBlockContainer> typeDefinition =
        blinded
            ? schemaDefinitions.getSignedBlindedBlockContainerSchema().getJsonTypeDefinition()
            : schemaDefinitions.getSignedBlockContainerSchema().getJsonTypeDefinition();

    return preferSszBlockEncoding.get()
        ? sendSignedBlockAsSszOrFallback(apiMethod, signedBlockContainer, typeDefinition)
        : sendSignedBlockAsJson(apiMethod, signedBlockContainer, typeDefinition);
  }

  private SendSignedBlockResult sendSignedBlockAsSszOrFallback(
      final ValidatorApiMethod apiMethod,
      final SignedBlockContainer signedBlockContainer,
      final DeserializableTypeDefinition<SignedBlockContainer> typeDefinition) {
    final SendSignedBlockResult result = sendSignedBlockAsSsz(apiMethod, signedBlockContainer);
    if (!result.isPublished() && !preferSszBlockEncoding.get()) {
      return sendSignedBlockAsJson(apiMethod, signedBlockContainer, typeDefinition);
    }
    return result;
  }

  private SendSignedBlockResult sendSignedBlockAsSsz(
      final ValidatorApiMethod apiMethod, final SignedBlockContainer signedBlockContainer) {
    return postOctetStream(
            apiMethod,
            Collections.emptyMap(),
            signedBlockContainer.sszSerialize().toArray(),
            sszResponseHandler)
        .map(__ -> SendSignedBlockResult.success(signedBlockContainer.getRoot()))
        .orElseGet(() -> SendSignedBlockResult.notImported("UNKNOWN"));
  }

  private SendSignedBlockResult sendSignedBlockAsJson(
      final ValidatorApiMethod apiMethod,
      final SignedBlockContainer signedBlockContainer,
      final DeserializableTypeDefinition<SignedBlockContainer> typeDefinition) {
    return postJson(
            apiMethod,
            Collections.emptyMap(),
            signedBlockContainer,
            typeDefinition,
            new ResponseHandler<>())
        .map(__ -> SendSignedBlockResult.success(signedBlockContainer.getRoot()))
        .orElseGet(() -> SendSignedBlockResult.notImported("UNKNOWN"));
  }

  private Optional<Object> handleUnsupportedSszRequest(
      final Request request, final Response response) {
    preferSszBlockEncoding.set(false);
    return Optional.empty();
  }
}

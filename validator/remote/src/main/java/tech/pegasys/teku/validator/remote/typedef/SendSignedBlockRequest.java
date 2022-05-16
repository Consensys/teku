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

package tech.pegasys.teku.validator.remote.typedef;

import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_BLINDED_BLOCK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_BLOCK;

import java.util.Collections;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;

public class SendSignedBlockRequest extends AbstractTypeDefRequest {
  private final boolean preferOctetStream;

  public SendSignedBlockRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final boolean preferOctetStream) {
    super(baseEndpoint, okHttpClient);
    this.preferOctetStream = preferOctetStream;
  }

  public SendSignedBlockResult sendSignedBlock(SignedBeaconBlock signedBeaconBlock) {
    final ValidatorApiMethod apiMethod =
        signedBeaconBlock.getMessage().getBody().isBlinded()
            ? SEND_SIGNED_BLINDED_BLOCK
            : SEND_SIGNED_BLOCK;

    if (preferOctetStream) {
      return postOctetStream(
              apiMethod,
              Collections.emptyMap(),
              signedBeaconBlock.sszSerialize().toArray(),
              new ResponseHandler<>())
          .map(__ -> SendSignedBlockResult.success(Bytes32.ZERO))
          .orElseGet(() -> SendSignedBlockResult.notImported("UNKNOWN"));
    }

    return postJson(
            apiMethod,
            Collections.emptyMap(),
            signedBeaconBlock,
            signedBeaconBlock.getSchema().getJsonTypeDefinition(),
            new ResponseHandler<>())
        .map(__ -> SendSignedBlockResult.success(Bytes32.ZERO))
        .orElseGet(() -> SendSignedBlockResult.notImported("UNKNOWN"));
  }
}

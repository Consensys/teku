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

import static tech.pegasys.teku.builder.rest.BuilderApiMethod.SEND_SIGNED_BEACON_BLOCK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.util.Locale;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.builder.rest.ResponseHandler;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class SendSignedBeaconBlockRequest extends AbstractBuilderRequest {

  private final Spec spec;

  public SendSignedBeaconBlockRequest(
      final Spec spec, final HttpUrl baseEndpoint, final OkHttpClient httpClient) {
    super(baseEndpoint, httpClient);
    this.spec = spec;
  }

  public void submit(final SignedBeaconBlock signedBeaconBlock) {
    final String consensusVersion =
        spec.atSlot(signedBeaconBlock.getSlot()).getMilestone().name().toLowerCase(Locale.ROOT);
    postOctetStream(
        SEND_SIGNED_BEACON_BLOCK,
        Map.of(),
        Map.of(HEADER_CONSENSUS_VERSION, consensusVersion),
        signedBeaconBlock.sszSerialize().toArrayUnsafe(),
        new ResponseHandler<>());
  }
}

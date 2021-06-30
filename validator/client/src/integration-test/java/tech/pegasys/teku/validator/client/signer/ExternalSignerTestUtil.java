/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.matchers.MatchType.STRICT;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.MediaType;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.infrastructure.metrics.StubCounter;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;

class ExternalSignerTestUtil {
  private static final JsonProvider jsonProvider = new JsonProvider();

  static void verifySignRequest(
      final ClientAndServer client,
      final String publicKey,
      final SigningRequestBody signingRequestBody)
      throws JsonProcessingException {
    client.verify(
        request()
            .withMethod("POST")
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(json(jsonProvider.objectToJSON(signingRequestBody), STRICT))
            .withPath(ExternalSigner.EXTERNAL_SIGNER_ENDPOINT + "/" + publicKey));
  }

  static Map<String, Object> createForkInfo(final ForkInfo forkInfo) {
    return Map.of(
        "genesis_validators_root",
        forkInfo.getGenesisValidatorsRoot(),
        "fork",
        new Fork(forkInfo.getFork()));
  }

  static void validateMetrics(
      final StubMetricsSystem metricsSystem,
      final long successCount,
      final long failCount,
      final long timeoutCount) {
    final StubCounter labelledCounter =
        metricsSystem.getCounter(TekuMetricCategory.VALIDATOR, "external_signer_requests");
    assertThat(labelledCounter.getValue("success")).isEqualTo(successCount);
    assertThat(labelledCounter.getValue("failed")).isEqualTo(failCount);
    assertThat(labelledCounter.getValue("timeout")).isEqualTo(timeoutCount);
  }
}

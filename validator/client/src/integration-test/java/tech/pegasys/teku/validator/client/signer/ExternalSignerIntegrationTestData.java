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

package tech.pegasys.teku.validator.client.signer;

import java.net.URL;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueueWithPriority;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.loader.HttpClientExternalSignerFactory;

public class ExternalSignerIntegrationTestData {
  static final Duration TIMEOUT = Duration.ofMillis(500);
  private final Spec spec;
  private final DataStructureUtil dataStructureUtil;
  private final SigningRootUtil signingRootUtil;
  private final ForkInfo fork;

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final ThrottlingTaskQueueWithPriority queue =
      ThrottlingTaskQueueWithPriority.create(
          8, metricsSystem, TekuMetricCategory.VALIDATOR, "externalSignerTest");

  public ExternalSignerIntegrationTestData(final Spec spec) {
    this.spec = spec;
    dataStructureUtil = new DataStructureUtil(spec);
    signingRootUtil = new SigningRootUtil(spec);
    fork = dataStructureUtil.randomForkInfo();
  }

  public Spec getSpec() {
    return spec;
  }

  public DataStructureUtil getDataStructureUtil() {
    return dataStructureUtil;
  }

  public SigningRootUtil getSigningRootUtil() {
    return signingRootUtil;
  }

  public ForkInfo getFork() {
    return fork;
  }

  public StubMetricsSystem getMetricsSystem() {
    return metricsSystem;
  }

  public ThrottlingTaskQueueWithPriority getQueue() {
    return queue;
  }

  public HttpClient getHttpClient(final URL externalSignerUrl, final List<String> publicKeys) {
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerPublicKeySources(publicKeys)
            .validatorExternalSignerUrl(externalSignerUrl)
            .validatorExternalSignerTimeout(TIMEOUT)
            .build();

    return new HttpClientExternalSignerFactory(config).get();
  }
}

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

package tech.pegasys.teku.validator.client.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.api.ValidatorConfig;

public class ExternalValidatorSourceTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final PublicKeyLoader publicKeyLoader = mock(PublicKeyLoader.class);
  private final HttpClient httpClient = mock(HttpClient.class);
  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private final AsyncRunner asyncRunner = new StubAsyncRunner();

  @SuppressWarnings("unchecked")
  private final HttpResponse<Void> httpResponse = mock(HttpResponse.class);

  private ValidatorConfig config;
  private ValidatorSource validatorSource;

  public ExternalValidatorSourceTest() {}

  @BeforeEach
  void setup() throws IOException, InterruptedException {
    config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(new URL("http://localhost:9000"))
            .build();
    when(httpResponse.statusCode()).thenReturn(SC_OK);
    when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
        .thenReturn(httpResponse);
    validatorSource =
        ExternalValidatorSource.create(
            spec, metricsSystem, config, () -> httpClient, publicKeyLoader, asyncRunner);
  }

  @Test
  void shouldThrowExceptionWhenAddValidator() {
    assertThatThrownBy(() -> validatorSource.addValidator(null, "pass", BLSPublicKey.empty()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldThrowExceptionWhenDeleteValidator() {
    assertThatThrownBy(() -> validatorSource.deleteValidator(BLSPublicKey.empty()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldSayFalseToAddValidators() {
    assertThat(validatorSource.canUpdateValidators()).isFalse();
  }
}

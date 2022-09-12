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

package tech.pegasys.teku.validator.remote;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

class RemoteBeaconNodeApiTest {
  private final ServiceConfig serviceConfig = mock(ServiceConfig.class);
  private final AsyncRunner asyncRunner = new StubAsyncRunner();
  private final Spec spec = TestSpecFactory.createMinimalAltair();

  @Test
  void producesExceptionWhenInvalidUrlPassed() {
    assertThatThrownBy(
            () ->
                RemoteBeaconNodeApi.create(
                    serviceConfig,
                    asyncRunner,
                    List.of(new URI("notvalid")),
                    spec,
                    false,
                    false,
                    true,
                    Duration.ofMillis(1),
                    Duration.ofMillis(1)))
        .hasMessageContaining("Failed to convert remote api endpoint");
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PingUtilsTest {

  private final MockWebServer mockWebServer = new MockWebServer();

  private HttpUrl hostUrl;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer.start();
    this.hostUrl = mockWebServer.url("/");
  }

  @Test
  void reachesHost() throws IOException {
    assertThat(PingUtils.hostIsReachable(hostUrl)).isTrue();
    mockWebServer.close();
  }

  @Test
  void doesNotReachHost() throws IOException {
    mockWebServer.close();
    assertThat(PingUtils.hostIsReachable(hostUrl)).isFalse();
  }
}

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

package tech.pegasys.teku.ethereum.executionclient.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.ethereum.executionclient.auth.JwtTestHelper.generateJwtSecret;

import com.google.common.net.HttpHeaders;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.websocket.WebSocketClient;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class JwtAuthWebsocketHelperTest {
  private final TimeProvider timeProvider = mock(TimeProvider.class);

  @Test
  public void shouldSetTimedAuthHeader() {
    JwtConfig jwtConfig = new JwtConfig(generateJwtSecret());
    TokenProvider tokenProvider = new TokenProvider(jwtConfig);
    WebSocketClient webSocketClient = mock(WebSocketClient.class);
    final JwtAuthWebsocketHelper jwtAuthWebsocketHelper =
        new JwtAuthWebsocketHelper(jwtConfig, timeProvider);
    UInt64 timeOne = UInt64.valueOf(123);
    Token expectedTokenOne = tokenProvider.token(timeOne).orElseThrow();
    when(timeProvider.getTimeInMillis()).thenReturn(timeOne);
    jwtAuthWebsocketHelper.setAuth(webSocketClient);
    verify(webSocketClient)
        .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + expectedTokenOne.getJwtToken());
    UInt64 timeTwo =
        timeOne.plus(UInt64.ONE).plus(TimeUnit.SECONDS.toMillis(JwtConfig.EXPIRES_IN_SECONDS));
    Token expectedTokenTwo = tokenProvider.token(timeTwo).orElseThrow();
    assertThat(expectedTokenTwo).isNotEqualTo(expectedTokenOne);
    when(timeProvider.getTimeInMillis()).thenReturn(timeTwo);
    jwtAuthWebsocketHelper.setAuth(webSocketClient);
    verify(webSocketClient)
        .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + expectedTokenTwo.getJwtToken());
  }
}

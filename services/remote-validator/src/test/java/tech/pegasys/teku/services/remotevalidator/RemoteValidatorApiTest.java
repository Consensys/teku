/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.services.remotevalidator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import io.javalin.websocket.WsConnectContext;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.remotevalidator.RemoteValidatorSubscriptions.SubscriptionStatus;
import tech.pegasys.teku.util.config.TekuConfiguration;

class RemoteValidatorApiTest {

  private final TekuConfiguration configuration = mock(TekuConfiguration.class);

  private final RemoteValidatorSubscriptions subscriptionManager =
      mock(RemoteValidatorSubscriptions.class);

  private final Session wsSession = mock(Session.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Consumer<BeaconChainEvent>> subscriberCallbackArgCaptor =
      ArgumentCaptor.forClass(Consumer.class);

  private final WsConnectContext wsContext = createWsContextStub();

  private final RemoteValidatorApi remoteValidatorApi =
      new RemoteValidatorApi(configuration, subscriptionManager);

  @BeforeEach
  public void beforeEach() {
    reset(configuration, subscriptionManager, wsSession);
  }

  @Test
  public void onConnectCallback_ShouldSubscribeValidatorWithSessionId() {
    final String sessionId = wsContext.getSessionId();

    when(subscriptionManager.subscribe(any(), any())).thenReturn(SubscriptionStatus.success());

    remoteValidatorApi.subscribeValidator(wsContext);

    verify(subscriptionManager).subscribe(eq(sessionId), any());
  }

  @Test
  public void onConnectCallback_WhenSubscribeFails_ShouldCloseSession() {
    final SubscriptionStatus failedSubscriptionStatus = SubscriptionStatus.maxSubscribers();
    when(subscriptionManager.subscribe(any(), any())).thenReturn(failedSubscriptionStatus);

    remoteValidatorApi.subscribeValidator(wsContext);

    verify(wsSession).close(eq(StatusCode.NORMAL), eq(failedSubscriptionStatus.getInfo()));
  }

  @Test
  public void onConnectCallback_SubscriptionCallbackShouldSendEventOverTheWire() throws Exception {
    when(subscriptionManager.subscribe(any(), any())).thenReturn(SubscriptionStatus.success());
    final RemoteEndpoint remoteEndpoint = mock(RemoteEndpoint.class);
    when(wsSession.getRemote()).thenReturn(remoteEndpoint);

    remoteValidatorApi.subscribeValidator(wsContext);
    verify(subscriptionManager).subscribe(any(), subscriberCallbackArgCaptor.capture());

    final Consumer<BeaconChainEvent> subscriberCallback = subscriberCallbackArgCaptor.getValue();
    subscriberCallback.accept(new BeaconChainEvent("foo", UInt64.ONE));

    verify(remoteEndpoint).sendString(eq("{\"name\":\"foo\",\"data\":\"1\"}"));
  }

  @Test
  public void onConnectCallback_SubscriptionCallbackShouldCloseSessionOnFailure() throws Exception {
    when(subscriptionManager.subscribe(any(), any())).thenReturn(SubscriptionStatus.success());
    final RemoteEndpoint remoteEndpoint = mock(RemoteEndpoint.class);
    doThrow(new IOException()).when(remoteEndpoint).sendString(any());
    when(wsSession.getRemote()).thenReturn(remoteEndpoint);

    remoteValidatorApi.subscribeValidator(wsContext);
    verify(subscriptionManager).subscribe(any(), subscriberCallbackArgCaptor.capture());

    final Consumer<BeaconChainEvent> subscriberCallback = subscriberCallbackArgCaptor.getValue();
    subscriberCallback.accept(new BeaconChainEvent("foo", UInt64.ONE));

    verify(wsSession)
        .close(eq(StatusCode.SERVER_ERROR), eq("Unexpected error on Remote Validator server"));
  }

  @Test
  public void onCloseCallback_ShouldUnsubscribeAndCloseSession() {
    final String sessionId = wsContext.getSessionId();
    remoteValidatorApi.unsubscribeValidator(wsContext);

    verify(wsSession).close();
    verify(subscriptionManager).unsubscribe(eq(sessionId));
  }

  /*
   Handling Javalin init with mocking
  */
  private WsConnectContext createWsContextStub() {
    final Context context = mock(Context.class);
    final HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    final ServletUpgradeRequest servletUpgradeRequest = mock(ServletUpgradeRequest.class);

    when(httpServletRequest.getAttribute("javalin-ws-upgrade-context")).thenReturn(context);
    when(servletUpgradeRequest.getHttpServletRequest()).thenReturn(httpServletRequest);
    when(wsSession.getUpgradeRequest()).thenReturn(servletUpgradeRequest);

    return new WsConnectContext(UUID.randomUUID().toString(), wsSession);
  }
}

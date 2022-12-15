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

package tech.pegasys.teku.test.acceptance.dsl;

import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.ReadyState;
import java.net.URI;
import java.util.List;

public class EventStreamListener {
  EventSource eventSource;
  final Eth2EventHandler handler = new Eth2EventHandler();

  public EventStreamListener(final String url) {
    eventSource = new EventSource.Builder(handler, URI.create(url)).build();
    eventSource.start();
  }

  public List<Eth2EventHandler.PackedMessage> getMessages() {
    return handler.getMessages();
  }

  public boolean isReady() {
    return eventSource.getState() == ReadyState.OPEN && handler.hasReceivedComment();
  }

  public void close() {
    eventSource.close();
  }
}

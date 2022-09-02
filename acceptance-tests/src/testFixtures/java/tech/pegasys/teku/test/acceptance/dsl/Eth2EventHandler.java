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

import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Eth2EventHandler implements EventHandler {
  private final List<PackedMessage> eventList = new ArrayList<>();

  @Override
  public void onOpen() {}

  @Override
  public void onClosed() {}

  @Override
  public void onMessage(final String event, final MessageEvent messageEvent) {
    eventList.add(new PackedMessage(event, messageEvent));
  }

  public List<PackedMessage> getMessages() {
    return Collections.unmodifiableList(eventList);
  }

  @Override
  public void onComment(final String comment) {}

  @Override
  public void onError(final Throwable t) {}

  public static class PackedMessage {
    private String event;
    private MessageEvent messageEvent;

    public PackedMessage(final String event, final MessageEvent messageEvent) {
      this.event = event;
      this.messageEvent = messageEvent;
    }

    public String getEvent() {
      return event;
    }

    public MessageEvent getMessageEvent() {
      return messageEvent;
    }
  }
}

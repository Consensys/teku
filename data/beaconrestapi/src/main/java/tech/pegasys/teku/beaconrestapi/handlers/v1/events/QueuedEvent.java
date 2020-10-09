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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import java.util.Objects;
import tech.pegasys.teku.api.response.v1.EventType;

public class QueuedEvent {
  private final EventType eventType;
  private final String messageData;

  private QueuedEvent(final EventType eventType, final String messageData) {
    this.eventType = eventType;
    this.messageData = messageData;
  }

  public static QueuedEvent of(final EventType eventType, final String messageData) {
    return new QueuedEvent(eventType, messageData);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final QueuedEvent that = (QueuedEvent) o;
    return eventType == that.eventType && Objects.equals(messageData, that.messageData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventType, messageData);
  }

  public EventType getEventType() {
    return eventType;
  }

  public String getMessageData() {
    return messageData;
  }
}

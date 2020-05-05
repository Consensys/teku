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

package tech.pegasys.teku.util;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.List;

public class EventSink<T> {

  private final List<T> events = new ArrayList<>();
  private final Class<T> eventType;

  private EventSink(final Class<T> eventType) {
    this.eventType = eventType;
  }

  public static <T> List<T> capture(final EventBus eventBus, Class<T> eventType) {
    final EventSink<T> eventSink = new EventSink<>(eventType);
    eventBus.register(eventSink);
    return eventSink.events;
  }

  @Subscribe
  public void onEvent(final Object event) {
    if (eventType.isInstance(event)) {
      events.add(eventType.cast(event));
    }
  }
}

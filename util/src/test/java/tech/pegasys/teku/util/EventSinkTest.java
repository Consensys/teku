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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.eventbus.EventBus;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventSinkTest {
  private final EventBus eventBus = new EventBus();

  @Test
  public void shouldCaptureOnlyTheDeclaredEventType() {
    final List<String> capturedEvents = EventSink.capture(eventBus, String.class);

    eventBus.post("Hello");
    eventBus.post(BigInteger.ZERO);
    eventBus.post("World");

    assertThat(capturedEvents).containsExactly("Hello", "World");
  }

  @Test
  public void shouldCaptureSubclassesOfDeclaredType() {
    final List<Exception> capturedEvents = EventSink.capture(eventBus, Exception.class);

    final Exception sameClass = new Exception();
    final RuntimeException subclass = new RuntimeException();
    final Throwable superClass = new Throwable();
    eventBus.post(sameClass);
    eventBus.post(subclass);
    eventBus.post(superClass);

    assertThat(capturedEvents).containsExactly(sameClass, subclass);
  }
}

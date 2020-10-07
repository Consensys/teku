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

package tech.pegasys.teku.pow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

class Eth1StatusLoggerTest {

  private Eth1StatusLogger eth1StatusLogger;
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final TimeProvider timeProvider = mock(TimeProvider.class);

  @BeforeEach
  public void setUp() {
    this.eth1StatusLogger = new Eth1StatusLogger(asyncRunner, timeProvider);
  }

  @Test
  public void shouldStartLoggerIfNotRunningAndFails() {
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    eth1StatusLogger.fail();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
  }

  @Test
  public void shouldCancelLoggerIfRunningAndSuccess() {
    eth1StatusLogger.fail();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    eth1StatusLogger.success();
    asyncRunner.executeQueuedActions(1);
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }
}

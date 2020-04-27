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

package tech.pegasys.teku.api.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

public class LogLevelTest {

  @Test
  public void shouldAcceptDebugLogLevelInUpperCase() {
    final LogLevel level = new LogLevel("INFO");

    assertThat(level.getLevel()).isEqualTo(Level.INFO);
  }

  @Test
  public void shouldAcceptDebugLogLevelInLowerCase() {
    final LogLevel level = new LogLevel("info");

    assertThat(level.getLevel()).isEqualTo(Level.INFO);
  }

  @Test
  public void shouldExceptionWhenLogLevelInvalid() {

    assertThatThrownBy(() -> new LogLevel("I'm an invalid log level"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown level constant [I'M AN INVALID LOG LEVEL].");
  }
}

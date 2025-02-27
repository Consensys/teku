/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class LoggingDestinationTest {

  private TestAppender testAppender;

  @BeforeEach
  void setUp() {
    testAppender = new TestAppender();
    testAppender.start();
    final Logger logger = (Logger) LogManager.getLogger(LoggingDestination.class);
    logger.addAppender(testAppender);
    logger.setLevel(Level.WARN);
  }

  @AfterEach
  void tearDown() {
    final Logger logger = (Logger) LogManager.getLogger(LoggingDestination.class);
    logger.removeAppender(testAppender);
    testAppender.stop();
  }

  @Test
  void shouldReturnValidDestination() {
    assertThat(LoggingDestination.get("console")).isEqualTo(LoggingDestination.CONSOLE);
    assertThat(LoggingDestination.get("CONSOLE")).isEqualTo(LoggingDestination.CONSOLE);
    assertThat(LoggingDestination.get("both")).isEqualTo(LoggingDestination.BOTH);
    assertThat(LoggingDestination.get("default")).isEqualTo(LoggingDestination.DEFAULT_BOTH);
    assertThat(LoggingDestination.get("file")).isEqualTo(LoggingDestination.FILE);
    assertThat(LoggingDestination.get("custom")).isEqualTo(LoggingDestination.CUSTOM);
    
    // No warnings should be logged for valid destinations
    assertThat(testAppender.getLogEvents()).isEmpty();
  }

  @Test
  void shouldLogWarningAndReturnDefaultForInvalidDestination() {
    assertThat(LoggingDestination.get("invalid")).isEqualTo(LoggingDestination.DEFAULT_BOTH);
    
    // Warning should be logged for invalid destination
    assertThat(testAppender.getLogEvents()).hasSize(1);
    LogEvent logEvent = testAppender.getLogEvents().get(0);
    assertThat(logEvent.getLevel()).isEqualTo(Level.WARN);
    assertThat(logEvent.getMessage().getFormattedMessage())
        .contains("Invalid logging destination 'invalid' provided");
    assertThat(logEvent.getMessage().getFormattedMessage())
        .contains("Valid options are: both, console, default, file, custom");
  }

  private static class TestAppender extends AbstractAppender {
    private final List<LogEvent> logEvents = new ArrayList<>();

    TestAppender() {
      super("TestAppender", null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      logEvents.add(event.toImmutable());
    }

    public List<LogEvent> getLogEvents() {
      return logEvents;
    }
  }
} 

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

package tech.pegasys.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.ErrorHandler;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.spi.ExtendedLogger;

public class LogCaptor implements AutoCloseable {
  private static final AtomicInteger UNIQUEIFIER = new AtomicInteger();
  private final LoggerConfig loggerConfig;
  private final CapturingAppender appender;

  public LogCaptor(final LoggerConfig loggerConfig, final CapturingAppender appender) {

    this.loggerConfig = loggerConfig;
    this.appender = appender;
  }

  public void assertMessagesInOrder(final Level level, final String... messages) {
    assertThat(getMessages(level)).containsSubsequence(messages);
  }

  public void assertInfoLog(final String message) {
    assertLogged(Level.INFO, message);
  }

  public void assertErrorLog(final String message) {
    assertLogged(Level.ERROR, message);
  }

  public void assertFatalLog(final String message) {
    assertLogged(Level.FATAL, message);
  }

  public void assertLogged(final Level level, final String message) {
    assertThat(getMessages(level)).contains(message);
  }

  private Stream<String> getMessages(final Level level) {
    return appender.logs.stream()
        .filter(log -> log.getLevel().equals(level))
        .map(log -> log.getMessage().getFormattedMessage());
  }

  public static LogCaptor forClass(final Class<?> clazz) {
    final LoggerContext context = LoggerContext.getContext(false);
    final ExtendedLogger logger = context.getLogger(clazz);
    final CapturingAppender appender =
        new CapturingAppender("LogCaptorAppender" + UNIQUEIFIER.incrementAndGet());
    final LoggerConfig loggerConfig = context.getConfiguration().getLoggerConfig(logger.getName());
    loggerConfig.addAppender(appender, null, null);
    return new LogCaptor(loggerConfig, appender);
  }

  @Override
  public void close() {
    loggerConfig.removeAppender(appender.getName());
  }

  private static class CapturingAppender implements Appender {
    private static final PatternLayout LAYOUT =
        PatternLayout.newBuilder().withPattern("%msg").build();
    private final List<LogEvent> logs = new ArrayList<>();
    private final String name;

    private CapturingAppender(final String name) {
      this.name = name;
    }

    @Override
    public void append(final LogEvent event) {
      logs.add(event.toImmutable());
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Layout<? extends Serializable> getLayout() {
      return LAYOUT;
    }

    @Override
    public boolean ignoreExceptions() {
      return false;
    }

    @Override
    public ErrorHandler getHandler() {
      return null;
    }

    @Override
    public void setHandler(final ErrorHandler handler) {}

    @Override
    public State getState() {
      return State.STARTED;
    }

    @Override
    public void initialize() {}

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public boolean isStarted() {
      return true;
    }

    @Override
    public boolean isStopped() {
      return false;
    }
  }
}

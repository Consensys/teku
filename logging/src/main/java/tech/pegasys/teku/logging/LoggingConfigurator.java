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

package tech.pegasys.teku.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.OnStartupTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.status.StatusLogger;

public class LoggingConfigurator {

  public static final String EVENT_LOGGER_NAME = "teku-event-log";
  public static final String STATUS_LOGGER_NAME = "teku-status-log";

  private static final String CONSOLE_APPENDER_NAME = "teku-console-appender";
  private static final String CONSOLE_FORMAT = "%d{HH:mm:ss.SSS} [%-5level] - %msg%n";
  private static final String FILE_APPENDER_NAME = "teku-log-appender";
  private static final String FILE_FORMAT =
      "%d{yyyy-MM-dd HH:mm:ss.SSSZZZ} | %t | %-5level | %c{1} | %msg%n";

  private static LoggingDestination DESTINATION;
  private static boolean COLOR;
  private static boolean INCLUDE_EVENTS;
  private static String FILE;
  private static String FILE_PATTERN;

  // TODO get the log level from the root looger i.e the config file?
  private static Level LOG_LEVEL = Level.INFO;

  public static boolean isColorEnabled() {
    return COLOR;
  }

  public static void setAllLevels(final Level level) {
    StatusLogger.getLogger().info("Setting logging level to {}", level.name());
    Configurator.setAllLevels("", level);
    LOG_LEVEL = level;
  }

  public static void setDestination(final LoggingDestination destination) {
    LoggingConfigurator.DESTINATION = destination;
  }

  public static void setColor(final boolean enabled) {
    LoggingConfigurator.COLOR = enabled;
  }

  public static void setIncludeEvents(final boolean enabled) {
    LoggingConfigurator.INCLUDE_EVENTS = enabled;
  }

  public static void setFile(final String file) {
    LoggingConfigurator.FILE = file;
  }

  public static void setFilePattern(final String pattern) {
    FILE_PATTERN = pattern;
  }

  public static void update() {
    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    addLoggersProgrammatically((AbstractConfiguration) ctx.getConfiguration());
    ctx.updateLoggers();
  }

  public static void addLoggersProgrammatically(final AbstractConfiguration configuration) {

    if (DESTINATION == null) {
      return;
    }

    // TODO warning about color & file?
    // TODO message about what we're setting the logging to/as
    // TODO any appenders / loggers that will be removed (via remove())
    StatusLogger.getLogger().info("Programmatic logging setup: {}", DESTINATION);

    Appender consoleAppender;
    Appender fileAppender;

    switch (DESTINATION) {
      case CONSOLE_ONLY:
        consoleAppender = consoleAppender(configuration);

        setUpStatusLogger(consoleAppender);
        setUpEventsLogger(consoleAppender);

        addAppenderToRootLogger(configuration, consoleAppender);
        break;
      case FILE_ONLY:
        fileAppender = fileAppender(configuration);

        setUpStatusLogger(fileAppender);
        setUpEventsLogger(fileAppender);

        addAppenderToRootLogger(configuration, fileAppender);
        break;
      default:
      case BOTH:
        consoleAppender = consoleAppender(configuration);
        fileAppender = fileAppender(configuration);

        setUpStatusLogger(consoleAppender);
        setUpEventsLogger(consoleAppender);

        addAppenderToRootLogger(configuration, consoleAppender);
        addAppenderToRootLogger(configuration, fileAppender);
        break;
    }
  }

  private static void addAppenderToRootLogger(
      final AbstractConfiguration configuration, final Appender appender) {
    configuration.getRootLogger().addAppender(appender, null, null);
  }

  private static void setUpEventsLogger(final Appender appender) {
    final Level eventsLogLevel = INCLUDE_EVENTS ? LOG_LEVEL : Level.OFF;
    final LoggerConfig logger = new LoggerConfig(EVENT_LOGGER_NAME, eventsLogLevel, true);
    logger.addAppender(appender, eventsLogLevel, null);
  }

  private static void setUpStatusLogger(final Appender appender) {
    final LoggerConfig logger = new LoggerConfig(STATUS_LOGGER_NAME, LOG_LEVEL, true);
    logger.addAppender(appender, LOG_LEVEL, null);
  }

  private static Appender consoleAppender(final AbstractConfiguration configuration) {
    configuration.removeAppender(CONSOLE_APPENDER_NAME);

    final Layout<?> layout =
        PatternLayout.newBuilder()
            .withConfiguration(configuration)
            .withPattern(CONSOLE_FORMAT)
            .build();
    final Appender consoleAppender =
        ConsoleAppender.newBuilder().setName(CONSOLE_APPENDER_NAME).setLayout(layout).build();
    consoleAppender.start();

    return consoleAppender;
  }

  private static Appender fileAppender(final AbstractConfiguration configuration) {
    configuration.removeAppender(FILE_APPENDER_NAME);

    final Layout<?> layout =
        PatternLayout.newBuilder()
            .withConfiguration(configuration)
            .withPattern(FILE_FORMAT)
            .build();

    final Appender fileAppender =
        RollingFileAppender.newBuilder()
            .setName(FILE_APPENDER_NAME)
            .setLayout(layout)
            .withFileName(FILE)
            .withFilePattern(FILE_PATTERN)
            .withPolicy(
                CompositeTriggeringPolicy.createPolicy(
                    OnStartupTriggeringPolicy.createPolicy(1),
                    TimeBasedTriggeringPolicy.newBuilder()
                        .withInterval(1)
                        .withModulate(true)
                        .build()))
            .build();
    fileAppender.start();

    return fileAppender;
  }
}

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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.status.StatusLogger;
import tech.pegasys.teku.util.config.LoggingDestination;

public class LoggingConfigurator {

  static final String EVENT_LOGGER_NAME = "teku-event-log";
  static final String STATUS_LOGGER_NAME = "teku-status-log";

  private static final String LOG4J_CONFIG_FILE_KEY = "LOG4J_CONFIGURATION_FILE";
  private static final String LOG4J_LEGACY_CONFIG_FILE_KEY = "log4j.configurationFile";
  private static final String CONSOLE_APPENDER_NAME = "teku-console-appender";
  private static final String FILE_APPENDER_NAME = "teku-log-appender";
  private static final String FILE_MESSAGE_FORMAT =
      "%d{yyyy-MM-dd HH:mm:ss.SSSZZZ} | %t | %-5level | %c{1} | %msg%n";
  private static final AtomicBoolean COLOR = new AtomicBoolean();

  private static LoggingDestination DESTINATION;
  private static boolean INCLUDE_EVENTS;
  private static String FILE;
  private static String FILE_PATTERN;
  private static Level ROOT_LOG_LEVEL = Level.INFO;

  public static boolean isColorEnabled() {
    return COLOR.get();
  }

  public static synchronized void setAllLevels(final Level level) {
    StatusLogger.getLogger().info("Setting logging level to {}", level.name());
    Configurator.setAllLevels("", level);
    ROOT_LOG_LEVEL = level;
  }

  public static synchronized void setAllLevels(final String filter, final Level level) {
    StatusLogger.getLogger().info("Setting logging level on filter {} to {}", filter, level.name());
    Configurator.setAllLevels(filter, level);
  }

  public static synchronized void update(final LoggingConfiguration configuration) {
    COLOR.set(configuration.isColorEnabled());
    DESTINATION = configuration.getDestination();
    INCLUDE_EVENTS = configuration.isIncludeEventsEnabled();
    FILE = configuration.getFile();
    FILE_PATTERN = configuration.getFileNamePattern();

    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    addLoggers((AbstractConfiguration) ctx.getConfiguration());
    ctx.updateLoggers();
  }

  public static synchronized void addLoggersProgrammatically(
      final AbstractConfiguration configuration) {
    addLoggers(configuration);
  }

  private static void addLoggers(final AbstractConfiguration configuration) {

    if (isUninitialized()) {
      return;
    }

    if (isProgrammaticLoggingRedundant()) {
      displayCustomLog4jConfigUsed();
      return;
    }

    displayProgrammaticLoggingConfiguration();

    Appender consoleAppender;
    Appender fileAppender;

    switch (DESTINATION) {
      case CONSOLE:
        consoleAppender = consoleAppender(configuration, false);

        setUpStatusLogger(consoleAppender);
        setUpEventsLogger(consoleAppender);

        addAppenderToRootLogger(configuration, consoleAppender);
        break;
      case FILE:
        fileAppender = fileAppender(configuration);

        setUpStatusLogger(fileAppender);
        setUpEventsLogger(fileAppender);

        addAppenderToRootLogger(configuration, fileAppender);
        break;
      default:
        displayUnknownDestinationConfigured();
        // fall through
      case DEFAULT_BOTH:
        // fall through
      case BOTH:
        consoleAppender = consoleAppender(configuration, true);
        final LoggerConfig eventsLogger = setUpEventsLogger(consoleAppender);
        final LoggerConfig statusLogger = setUpStatusLogger(consoleAppender);
        configuration.addLogger(eventsLogger.getName(), eventsLogger);
        configuration.addLogger(statusLogger.getName(), statusLogger);

        fileAppender = fileAppender(configuration);

        setUpStatusLogger(consoleAppender);
        addAppenderToRootLogger(configuration, fileAppender);
        break;
    }

    configuration.getLoggerContext().updateLoggers();
  }

  private static void displayProgrammaticLoggingConfiguration() {
    switch (DESTINATION) {
      case CONSOLE:
        StatusLogger.getLogger().info("Configuring logging for destination: console");
        break;
      case FILE:
        StatusLogger.getLogger().info("Configuring logging for destination: file");
        StatusLogger.getLogger().info("Logging file location: {}", FILE);
        break;
      default:
        // fall through
      case DEFAULT_BOTH:
        // fall through
      case BOTH:
        StatusLogger.getLogger().info("Configuring logging for destination: console and file");
        StatusLogger.getLogger().info("Logging file location: {}", FILE);
        break;
    }

    StatusLogger.getLogger().info("Logging includes events: {}", INCLUDE_EVENTS);
    StatusLogger.getLogger().info("Logging includes color: {}", COLOR);
  }

  private static void displayCustomLog4jConfigUsed() {
    StatusLogger.getLogger()
        .info(
            "Custom logging configuration applied from: {}", getCustomLog4jConfigFile().orElse(""));
  }

  private static void displayUnknownDestinationConfigured() {
    StatusLogger.getLogger()
        .warn(
            "Unknown logging destination: {}, applying default: {}",
            DESTINATION,
            LoggingDestination.BOTH);
  }

  private static boolean isUninitialized() {
    return DESTINATION == null;
  }

  private static boolean isProgrammaticLoggingRedundant() {
    return DESTINATION == LoggingDestination.DEFAULT_BOTH && isCustomLog4jConfigFileProvided();
  }

  private static boolean isCustomLog4jConfigFileProvided() {
    return getCustomLog4jConfigFile().isPresent();
  }

  private static Optional<String> getCustomLog4jConfigFile() {
    return Optional.ofNullable(System.getenv(LOG4J_CONFIG_FILE_KEY))
        .or(() -> Optional.ofNullable(System.getProperty(LOG4J_CONFIG_FILE_KEY)))
        .or(() -> Optional.ofNullable(System.getenv(LOG4J_LEGACY_CONFIG_FILE_KEY)))
        .or(() -> Optional.ofNullable(System.getProperty(LOG4J_LEGACY_CONFIG_FILE_KEY)));
  }

  private static void addAppenderToRootLogger(
      final AbstractConfiguration configuration, final Appender appender) {
    configuration.getRootLogger().addAppender(appender, null, null);
  }

  private static LoggerConfig setUpEventsLogger(final Appender appender) {
    final Level eventsLogLevel = INCLUDE_EVENTS ? ROOT_LOG_LEVEL : Level.OFF;
    final LoggerConfig logger = new LoggerConfig(EVENT_LOGGER_NAME, eventsLogLevel, true);
    logger.addAppender(appender, eventsLogLevel, null);
    return logger;
  }

  private static LoggerConfig setUpStatusLogger(final Appender appender) {
    final LoggerConfig logger = new LoggerConfig(STATUS_LOGGER_NAME, ROOT_LOG_LEVEL, true);
    logger.addAppender(appender, ROOT_LOG_LEVEL, null);
    return logger;
  }

  private static Appender consoleAppender(
      final AbstractConfiguration configuration, final boolean omitStackTraces) {
    configuration.removeAppender(CONSOLE_APPENDER_NAME);

    final Layout<?> layout =
        PatternLayout.newBuilder()
            .withAlwaysWriteExceptions(!omitStackTraces)
            .withNoConsoleNoAnsi(true)
            .withConfiguration(configuration)
            .withPatternSelector(new ConsolePatternSelector(configuration, omitStackTraces))
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
            .withPattern(FILE_MESSAGE_FORMAT)
            .build();

    final Appender fileAppender =
        RollingFileAppender.newBuilder()
            .setName(FILE_APPENDER_NAME)
            .withAppend(true)
            .setLayout(layout)
            .withFileName(FILE)
            .withFilePattern(FILE_PATTERN)
            .withPolicy(
                CompositeTriggeringPolicy.createPolicy(
                    TimeBasedTriggeringPolicy.newBuilder()
                        .withInterval(1)
                        .withModulate(true)
                        .build()))
            .build();
    fileAppender.start();

    return fileAppender;
  }
}

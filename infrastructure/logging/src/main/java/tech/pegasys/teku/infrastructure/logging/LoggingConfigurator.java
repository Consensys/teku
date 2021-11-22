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

package tech.pegasys.teku.infrastructure.logging;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
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
import org.apache.logging.log4j.core.pattern.RegexReplacement;
import org.apache.logging.log4j.status.StatusLogger;

public class LoggingConfigurator {

  static final String COLOR_LOG_REGEX = "[\\p{Cntrl}&&[^\r\n\u001b]]";
  static final String NO_COLOR_LOG_REGEX = "[\\p{Cntrl}&&[^\r\n]]";

  static final String EVENT_LOGGER_NAME = "teku-event-log";
  static final String STATUS_LOGGER_NAME = "teku-status-log";
  static final String VALIDATOR_LOGGER_NAME = "teku-validator-log";
  static final String P2P_LOGGER_NAME = "teku-p2p-log";

  private static final String LOG4J_CONFIG_FILE_KEY = "LOG4J_CONFIGURATION_FILE";
  private static final String LOG4J_LEGACY_CONFIG_FILE_KEY = "log4j.configurationFile";
  private static final String CONSOLE_APPENDER_NAME = "teku-console-appender";
  private static final String FILE_APPENDER_NAME = "teku-log-appender";
  private static final String FILE_MESSAGE_FORMAT =
      "%d{yyyy-MM-dd HH:mm:ss.SSSZZZ} | %t | %-5level | %c{1} | %msg%n";
  private static final AtomicBoolean COLOR = new AtomicBoolean();

  private static LoggingDestination DESTINATION;
  private static boolean INCLUDE_EVENTS;
  private static boolean INCLUDE_VALIDATOR_DUTIES;
  private static boolean INCLUDE_P2P_WARNINGS;
  private static String FILE;
  private static String FILE_PATTERN;
  private static Level ROOT_LOG_LEVEL = Level.INFO;

  public static boolean isColorEnabled() {
    return COLOR.get();
  }

  public static boolean isIncludeP2pWarnings() {
    return INCLUDE_P2P_WARNINGS;
  }

  public static synchronized void setColorEnabled(final boolean isEnabled) {
    COLOR.set(isEnabled);
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

  public static synchronized void setAllLevelsSilently(final String filter, final Level level) {
    Configurator.setAllLevels(filter, level);
  }

  public static synchronized void update(final LoggingConfig configuration) {
    COLOR.set(configuration.isColorEnabled());
    DESTINATION = configuration.getDestination();
    INCLUDE_EVENTS = configuration.isIncludeEventsEnabled();
    INCLUDE_VALIDATOR_DUTIES = configuration.isIncludeValidatorDutiesEnabled();
    INCLUDE_P2P_WARNINGS = configuration.isIncludeP2pWarningsEnabled();
    FILE = configuration.getLogFile();
    FILE_PATTERN = configuration.getLogFileNamePattern();

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

    if (isProgrammaticLoggingDisabled()) {
      displayLoggingConfigurationDisabled();
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
        setUpValidatorLogger(consoleAppender);

        addAppenderToRootLogger(configuration, consoleAppender);
        break;
      case FILE:
        fileAppender = fileAppender(configuration);

        setUpStatusLogger(fileAppender);
        setUpEventsLogger(fileAppender);
        setUpValidatorLogger(fileAppender);

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
        final LoggerConfig validatorLogger = setUpValidatorLogger(consoleAppender);
        configuration.addLogger(eventsLogger.getName(), eventsLogger);
        configuration.addLogger(statusLogger.getName(), statusLogger);
        configuration.addLogger(validatorLogger.getName(), validatorLogger);

        fileAppender = fileAppender(configuration);

        setUpStatusLogger(consoleAppender);
        addAppenderToRootLogger(configuration, fileAppender);
        break;
    }
    StatusLogger.getLogger().info("Include P2P warnings set to: {}", INCLUDE_P2P_WARNINGS);
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
    StatusLogger.getLogger()
        .info("Logging includes validator duties: {}", INCLUDE_VALIDATOR_DUTIES);
    StatusLogger.getLogger().info("Logging includes color: {}", COLOR);
  }

  private static void displayCustomLog4jConfigUsed() {
    StatusLogger.getLogger()
        .info(
            "Custom logging configuration applied from: {}", getCustomLog4jConfigFile().orElse(""));
  }

  private static void displayLoggingConfigurationDisabled() {
    StatusLogger.getLogger().info("Teku logging configuration disabled.");
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

  @VisibleForTesting
  // returns the original destination
  static LoggingDestination setDestination(final LoggingDestination destination) {
    final LoggingDestination original = DESTINATION;
    DESTINATION = destination;
    return original;
  }

  private static boolean isProgrammaticLoggingDisabled() {
    return DESTINATION == LoggingDestination.CUSTOM;
  }

  private static boolean isProgrammaticLoggingRedundant() {
    return (DESTINATION == LoggingDestination.DEFAULT_BOTH
            || DESTINATION == LoggingDestination.CUSTOM)
        && isCustomLog4jConfigFileProvided();
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

  private static LoggerConfig setUpValidatorLogger(final Appender appender) {
    // Don't disable validator error logs unless the root log level disables error.
    final Level validatorLogLevel =
        INCLUDE_VALIDATOR_DUTIES || ROOT_LOG_LEVEL.isMoreSpecificThan(Level.ERROR)
            ? ROOT_LOG_LEVEL
            : Level.ERROR;
    final LoggerConfig logger = new LoggerConfig(VALIDATOR_LOGGER_NAME, validatorLogLevel, true);
    logger.addAppender(appender, ROOT_LOG_LEVEL, null);
    return logger;
  }

  @VisibleForTesting
  static PatternLayout consoleAppenderLayout(
      AbstractConfiguration configuration, final boolean omitStackTraces) {
    final Pattern logReplacement =
        Pattern.compile(isColorEnabled() ? COLOR_LOG_REGEX : NO_COLOR_LOG_REGEX);
    return PatternLayout.newBuilder()
        .withRegexReplacement(RegexReplacement.createRegexReplacement(logReplacement, ""))
        .withAlwaysWriteExceptions(!omitStackTraces)
        .withNoConsoleNoAnsi(true)
        .withConfiguration(configuration)
        .withPatternSelector(
            new ConsolePatternSelector(
                configuration, omitStackTraces, LoggingDestination.CONSOLE.equals(DESTINATION)))
        .build();
  }

  private static Appender consoleAppender(
      final AbstractConfiguration configuration, final boolean omitStackTraces) {
    configuration.removeAppender(CONSOLE_APPENDER_NAME);

    final Layout<?> layout = consoleAppenderLayout(configuration, omitStackTraces);

    final Appender consoleAppender =
        ConsoleAppender.newBuilder().setName(CONSOLE_APPENDER_NAME).setLayout(layout).build();
    consoleAppender.start();

    return consoleAppender;
  }

  @VisibleForTesting
  static PatternLayout fileAppenderLayout(AbstractConfiguration configuration) {
    final Pattern logReplacement =
        Pattern.compile(isColorEnabled() ? COLOR_LOG_REGEX : NO_COLOR_LOG_REGEX);
    return PatternLayout.newBuilder()
        .withRegexReplacement(RegexReplacement.createRegexReplacement(logReplacement, ""))
        .withPattern(FILE_MESSAGE_FORMAT)
        .withConfiguration(configuration)
        .build();
  }

  private static Appender fileAppender(final AbstractConfiguration configuration) {
    configuration.removeAppender(FILE_APPENDER_NAME);
    final Layout<?> layout = fileAppenderLayout(configuration);

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

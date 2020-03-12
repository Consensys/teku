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
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class LoggingConfigurator {

  public static final String EVENT_LOGGER_NAME = "teku-event-log";
  public static final String STATUS_LOGGER_NAME = "teku-status-log";

  private static final String CONSOLE_APPENDER_NAME = "teku-console";
  private static final String CONSOLE_FORMAT = "%d{HH:mm:ss.SSS} [%-5level] - %msg%n";
  private static final boolean ADDITIVITY = true;

  private static LoggingDestination DESTINATION;
  private static boolean COLOR;
  private static boolean INCLUDE_EVENTS;
  private static Level LOG_LEVEL = Level.INFO;

  public static boolean isColorEnabled() {
    return COLOR;
  }

  public static void setAllLevels(final Level level) {
    // TODO try the Status logger instead of sop
    System.out.println("Setting logging level to " + level.name());
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

  public static void update() {

    // TODO console appender

    // TODO file appender

    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    setUpLoggersProgrammatically(ctx);
    ctx.updateLoggers();
  }

  private static void setUpLoggersProgrammatically(final LoggerContext ctx) {
    final Configuration configuration = ctx.getConfiguration();
    final Layout<?> layout =
        PatternLayout.newBuilder()
            .withConfiguration(configuration)
            .withPattern(CONSOLE_FORMAT)
            .build();
    final Appender consoleAppender =
        ConsoleAppender.newBuilder().setName(CONSOLE_APPENDER_NAME).setLayout(layout).build();

    consoleAppender.start();
    configuration.addAppender(consoleAppender);

    setUpLogger(EVENT_LOGGER_NAME, consoleAppender, configuration);
    setUpLogger(STATUS_LOGGER_NAME, consoleAppender, configuration);
  }

  private static void setUpLogger(
      final String name, final Appender appender, final Configuration configuration) {
    final LoggerConfig logger = new LoggerConfig(name, LOG_LEVEL, ADDITIVITY);
    logger.addAppender(appender, LOG_LEVEL, null);
    configuration.addLogger(name, logger);
  }
}

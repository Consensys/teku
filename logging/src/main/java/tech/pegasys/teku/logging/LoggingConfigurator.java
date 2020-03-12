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
    final Configuration config = ctx.getConfiguration();
    final Layout<?> layout =
        PatternLayout.newBuilder()
            .withConfiguration(ctx.getConfiguration())
            .withPattern(CONSOLE_FORMAT)
            .build();
    final Appender consoleAppender =
        ConsoleAppender.newBuilder().setName(CONSOLE_APPENDER_NAME).setLayout(layout).build();

    consoleAppender.start();
    config.addAppender(consoleAppender);

    final LoggerConfig eventConsoleLogger =
        new LoggerConfig(EVENT_LOGGER_NAME, LOG_LEVEL, ADDITIVITY);
    eventConsoleLogger.addAppender(consoleAppender, LOG_LEVEL, null);
    config.addLogger(EVENT_LOGGER_NAME, eventConsoleLogger);

    final LoggerConfig statusConsoleLogger =
        new LoggerConfig(STATUS_LOGGER_NAME, LOG_LEVEL, ADDITIVITY);
    eventConsoleLogger.addAppender(consoleAppender, LOG_LEVEL, null);
    config.addLogger(STATUS_LOGGER_NAME, statusConsoleLogger);

    ctx.updateLoggers();
  }
}

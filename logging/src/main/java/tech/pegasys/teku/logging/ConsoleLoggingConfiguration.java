package tech.pegasys.teku.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class ConsoleLoggingConfiguration extends XmlConfiguration {

  private static final String CONSOLE_FORMAT = "%d{HH:mm:ss.SSS} [%-5level] - %msg%n";
  private static final String CONSOLE_APPENDER_NAME = "Console";

  //TODO common name used by ALogger too!
  private static final String LOGGER_NAME = "stdout";

  public ConsoleLoggingConfiguration(final LoggerContext loggerContext,
      final ConfigurationSource configSource) {
    super(loggerContext, configSource);
  }

  @Override
  protected void doConfigure() {
    super.doConfigure();

    final Appender consoleAppender = addConsoleAppender();
    addConsoleLogger(consoleAppender);
  }

  private Appender addConsoleAppender() {
    final Layout<?> layout = PatternLayout.newBuilder().withConfiguration(this)
        .withPattern(CONSOLE_FORMAT).build();
    final Appender consoleAppender = ConsoleAppender.newBuilder().setName(CONSOLE_APPENDER_NAME)
        .setLayout(layout)
        .build();
    consoleAppender.start();

    removeAppender(CONSOLE_APPENDER_NAME);
    addAppender(consoleAppender);

    return consoleAppender;
  }

  private void addConsoleLogger(final Appender consoleAppender) {
    final LoggerConfig config = new LoggerConfig(LOGGER_NAME, Level.INFO,false);
    config.addAppender(consoleAppender, Level.INFO, null);

    removeLogger(LOGGER_NAME);
    addLogger(LOGGER_NAME, config);
  }
}

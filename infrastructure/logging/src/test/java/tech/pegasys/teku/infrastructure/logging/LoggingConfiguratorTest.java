/*
 * Copyright 2021 ConsenSys AG.
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.CharArrayWriter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

public class LoggingConfiguratorTest {
  public static final String COLOR_STRING = "a\u001b[30mb\u001b[31mc\u001b[37md";
  public static final String ENDL = System.lineSeparator();

  public static final String CTRL_STRING = "a\u0001b\u001fc\u007fd";
  public static final String STRIPPED_COLOR_STRING = "a[30mb[31mc[37md" + ENDL;
  private WriterAppender appender;
  private final CharArrayWriter outContent = new CharArrayWriter();

  @Test
  void toFile_shouldPrintColorIfEnabled() {
    final boolean color = LoggingConfigurator.isColorEnabled();
    try {
      LoggingConfigurator.setColorEnabled(true);
      fileAppenderLog(COLOR_STRING);
      assertThat(outContent.toString()).endsWith(COLOR_STRING + ENDL);
    } finally {
      LoggingConfigurator.setColorEnabled(color);
    }
  }

  @Test
  void toFile_shouldIgnoreNonPrintables() {
    fileAppenderLog(CTRL_STRING);
    assertThat(outContent.toString()).endsWith("abcd" + ENDL);
  }

  @Test
  void toConsole_shouldIgnoreNonPrintables() {
    consoleAppenderLog(CTRL_STRING);
    assertThat(outContent.toString()).endsWith("abcd" + ENDL);
  }

  @Test
  void toConsole_shouldPrintColorIfEnabled() {
    final boolean color = LoggingConfigurator.isColorEnabled();
    try {
      LoggingConfigurator.setColorEnabled(true);
      consoleAppenderLog(COLOR_STRING);
      assertThat(outContent.toString()).endsWith(COLOR_STRING + ENDL);
    } finally {
      LoggingConfigurator.setColorEnabled(color);
    }
  }

  @Test
  void toFile_shouldStripColorIfNotEnabled() {
    final boolean color = LoggingConfigurator.isColorEnabled();
    try {
      LoggingConfigurator.setColorEnabled(false);
      fileAppenderLog(COLOR_STRING);
      // normally color codes won't get this far, but if they do the control character is removed
      assertThat(outContent.toString()).endsWith(STRIPPED_COLOR_STRING);
    } finally {
      LoggingConfigurator.setColorEnabled(color);
    }
  }

  @Test
  void toConsole_shouldStripColorIfNotEnabled() {
    final boolean color = LoggingConfigurator.isColorEnabled();
    try {
      LoggingConfigurator.setColorEnabled(false);
      consoleAppenderLog(COLOR_STRING);
      // normally color codes won't get this far, but if they do the control character is removed
      assertThat(outContent.toString()).endsWith(STRIPPED_COLOR_STRING);
    } finally {
      LoggingConfigurator.setColorEnabled(color);
    }
  }

  @Test
  void toFile_shouldNotStripEndLine() {
    fileAppenderLog("a\r\nb");
    assertThat(outContent.toString()).endsWith("a\r\nb" + ENDL);
  }

  @Test
  void toConsole_shouldNotStripEndLine() {
    consoleAppenderLog("a\r\nb");
    assertThat(outContent.toString()).endsWith("a\r\nb" + ENDL);
  }

  @Test
  void toFile_MessageShouldHaveEndLine() {
    fileAppenderLog("ab");
    assertThat(outContent.toString()).endsWith("ab" + ENDL);
  }

  @Test
  void toConsole_MessageShouldHaveEndLine() {
    consoleAppenderLog("ab");
    assertThat(outContent.toString()).endsWith("ab" + ENDL);
  }

  @Test
  void toConsole_shouldPrependDateFormat() {
    final LoggingDestination initialDestination =
        LoggingConfigurator.setDestination(LoggingDestination.CONSOLE);
    try {
      consoleAppenderLog("ab");
      assertThat(outContent.toString().indexOf("-")).isEqualTo(4);
    } finally {
      LoggingConfigurator.setDestination(initialDestination);
    }
  }

  @Test
  void toConsole_shouldNotPrependDateFormatIfDestinationIsBoth() {
    final LoggingDestination initialDestination =
        LoggingConfigurator.setDestination(LoggingDestination.BOTH);
    try {
      consoleAppenderLog("ab");
      // if not in console logging mode, position of '-' is directly before message in this format
      assertThat(outContent.toString().indexOf("-")).isEqualTo(19);
    } finally {
      LoggingConfigurator.setDestination(initialDestination);
    }
  }

  private void fileAppenderLog(final String message) {
    log(message, LoggingConfigurator.fileAppenderLayout(null));
  }

  private void consoleAppenderLog(final String message) {
    log(message, LoggingConfigurator.consoleAppenderLayout(null, true));
  }

  private void log(final String message, final PatternLayout layout) {
    try {
      appender =
          WriterAppender.newBuilder()
              .setTarget(outContent)
              .setLayout(layout)
              .setName("TestLogger")
              .build();
      appender.start();
      final LogEvent event =
          Log4jLogEvent.newBuilder()
              .setLoggerName("TestLogger")
              .setLoggerFqcn(LoggingConfiguratorTest.class.getName())
              .setMessage(new SimpleMessage(message))
              .build();
      appender.append(event);
    } finally {
      appender.stop();
    }
  }
}

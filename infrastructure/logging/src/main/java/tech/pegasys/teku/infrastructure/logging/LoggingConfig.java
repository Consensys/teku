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

public class LoggingConfig {
  public static final String DEFAULT_LOG_SHORT_FILE_NAME = "teku-node";
  public static final String DEFAULT_LOG_FILE_EXTENSION = "log";
  public static final String DEFAULT_LOG_PATTERN = "%d{yyyy-MM-dd}";
  public static final String DEFAULT_LOG_DIRECTORY = ".";

  private final boolean colorEnabled;
  private final boolean includeEventsEnabled;
  private final boolean includeValidatorDutiesEnabled;
  private final boolean includeP2pWarningsEnabled;
  private final LoggingDestination destination;
  private final String logFile;
  private final String logFileNamePattern;

  private LoggingConfig(
      final boolean colorEnabled,
      final boolean includeEventsEnabled,
      final boolean includeValidatorDutiesEnabled,
      final boolean includeP2pWarningsEnabled,
      final LoggingDestination destination,
      final String logFile,
      final String logFileNamePattern) {
    this.colorEnabled = colorEnabled;
    this.includeEventsEnabled = includeEventsEnabled;
    this.includeValidatorDutiesEnabled = includeValidatorDutiesEnabled;
    this.includeP2pWarningsEnabled = includeP2pWarningsEnabled;
    this.destination = destination;
    this.logFile = logFile;
    this.logFileNamePattern = logFileNamePattern;
  }

  public static LoggingConfigBuilder builder() {
    return new LoggingConfigBuilder();
  }

  public boolean isColorEnabled() {
    return colorEnabled;
  }

  public boolean isIncludeEventsEnabled() {
    return includeEventsEnabled;
  }

  public boolean isIncludeValidatorDutiesEnabled() {
    return includeValidatorDutiesEnabled;
  }

  public boolean isIncludeP2pWarningsEnabled() {
    return includeP2pWarningsEnabled;
  }

  public LoggingDestination getDestination() {
    return destination;
  }

  public String getLogFile() {
    return logFile;
  }

  public String getLogFileNamePattern() {
    return logFileNamePattern;
  }

  public static final class LoggingConfigBuilder {
    public static final String SEP = System.getProperty("file.separator");

    private boolean colorEnabled = true;
    private boolean includeEventsEnabled = true;
    private boolean includeValidatorDutiesEnabled = true;
    private boolean includeP2pWarningsEnabled = false;
    private LoggingDestination destination = LoggingDestination.DEFAULT_BOTH;

    private String logShortFileName = DEFAULT_LOG_SHORT_FILE_NAME;
    private String logFileExtension = DEFAULT_LOG_FILE_EXTENSION;
    private String logPattern = DEFAULT_LOG_PATTERN;
    private String logDirectory;
    private String logFile;
    private String logFileNamePattern;

    private LoggingConfigBuilder() {}

    private void initMissingDefaults() {
      if (logDirectory == null) {
        logDirectory = DEFAULT_LOG_DIRECTORY;
      }
      if (logFile == null) {
        logFile = logDirectory + SEP + logShortFileName + "." + logFileExtension;
      }
      if (logFileNamePattern == null) {
        logFileNamePattern =
            logDirectory + SEP + logShortFileName + "_" + logPattern + "." + logFileExtension;
      }
    }

    public LoggingConfigBuilder colorEnabled(boolean colorEnabled) {
      this.colorEnabled = colorEnabled;
      return this;
    }

    public LoggingConfigBuilder includeEventsEnabled(boolean includeEventsEnabled) {
      this.includeEventsEnabled = includeEventsEnabled;
      return this;
    }

    public LoggingConfigBuilder includeValidatorDutiesEnabled(
        boolean includeValidatorDutiesEnabled) {
      this.includeValidatorDutiesEnabled = includeValidatorDutiesEnabled;
      return this;
    }

    public LoggingConfigBuilder includeP2pWarningsEnabled(final boolean includeP2pWarningsEnabled) {
      this.includeP2pWarningsEnabled = includeP2pWarningsEnabled;
      return this;
    }

    public LoggingConfigBuilder destination(LoggingDestination destination) {
      this.destination = destination;
      return this;
    }

    public LoggingConfigBuilder logShortFileName(String logShortFileName) {
      this.logShortFileName = logShortFileName;
      return this;
    }

    public LoggingConfigBuilder logFileExtension(String logFileExtension) {
      this.logFileExtension = logFileExtension;
      return this;
    }

    public LoggingConfigBuilder logPattern(String logPattern) {
      this.logPattern = logPattern;
      return this;
    }

    public LoggingConfigBuilder logDirectory(String logDirectory) {
      this.logDirectory = logDirectory;
      return this;
    }

    public LoggingConfigBuilder logDirectoryDefault(String logDirectory) {
      if (this.logDirectory == null) {
        this.logDirectory = logDirectory;
      }
      return this;
    }

    public LoggingConfigBuilder logFile(String logFile) {
      this.logFile = logFile;
      return this;
    }

    public LoggingConfigBuilder logFileNamePattern(String logFileNamePattern) {
      this.logFileNamePattern = logFileNamePattern;
      return this;
    }

    public LoggingConfig build() {
      initMissingDefaults();
      return new LoggingConfig(
          colorEnabled,
          includeEventsEnabled,
          includeValidatorDutiesEnabled,
          includeP2pWarningsEnabled,
          destination,
          logFile,
          logFileNamePattern);
    }
  }
}

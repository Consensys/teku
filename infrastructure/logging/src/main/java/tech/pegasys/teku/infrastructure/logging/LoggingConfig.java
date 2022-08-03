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

package tech.pegasys.teku.infrastructure.logging;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import org.apache.logging.log4j.Level;

public class LoggingConfig {
  public static final String DEFAULT_LOG_FILE_NAME_PREFIX = "teku-node";
  public static final String DEFAULT_LOG_FILE_NAME_SUFFIX = ".log";
  public static final String DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX = "_%d{yyyy-MM-dd}.log";

  public static final String DATA_LOG_SUBDIRECTORY = "logs";
  public static final int DEFAULT_DB_OP_ALERT_THRESHOLD_MILLIS = 0;
  private final Optional<Level> logLevel;
  private final boolean colorEnabled;
  private final boolean includeEventsEnabled;
  private final boolean includeValidatorDutiesEnabled;
  private final boolean includeP2pWarningsEnabled;
  private final LoggingDestination destination;
  private final String logFile;
  private final String logFileNamePattern;
  private final int dbOpAlertThresholdMillis;

  private LoggingConfig(
      final Optional<Level> logLevel,
      final boolean colorEnabled,
      final boolean includeEventsEnabled,
      final boolean includeValidatorDutiesEnabled,
      final boolean includeP2pWarningsEnabled,
      final LoggingDestination destination,
      final String logFile,
      final String logFileNamePattern,
      final int dbOpAlertThresholdMillis) {
    this.logLevel = logLevel;
    this.colorEnabled = colorEnabled;
    this.includeEventsEnabled = includeEventsEnabled;
    this.includeValidatorDutiesEnabled = includeValidatorDutiesEnabled;
    this.includeP2pWarningsEnabled = includeP2pWarningsEnabled;
    this.destination = destination;
    this.logFile = logFile;
    this.logFileNamePattern = logFileNamePattern;
    this.dbOpAlertThresholdMillis = dbOpAlertThresholdMillis;
  }

  public static LoggingConfigBuilder builder() {
    return new LoggingConfigBuilder();
  }

  public Optional<Level> getLogLevel() {
    return logLevel;
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

  public int getDbOpAlertThresholdMillis() {
    return dbOpAlertThresholdMillis;
  }

  public static final class LoggingConfigBuilder {
    public static final String SEP = System.getProperty("file.separator");

    private Optional<Level> logLevel = Optional.empty();
    private boolean colorEnabled = true;
    private boolean includeEventsEnabled = true;
    private boolean includeValidatorDutiesEnabled = true;
    private boolean includeP2pWarningsEnabled = false;
    private LoggingDestination destination = LoggingDestination.DEFAULT_BOTH;

    private String logFileNamePrefix = DEFAULT_LOG_FILE_NAME_PREFIX;
    private String logFileName;
    private String logFileNamePattern;

    private String dataDirectory;
    private String logDirectory;
    private String logPath;
    private String logPathPattern;
    private int dbOpAlertThresholdMillis;

    private LoggingConfigBuilder() {}

    private void initMissingDefaults() {
      if (logPath == null || logPathPattern == null) {
        if (logFileName == null) {
          logFileName = logFileNamePrefix + DEFAULT_LOG_FILE_NAME_SUFFIX;
        }
        if (logFileNamePattern == null) {
          logFileNamePattern = logFileNamePrefix + DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX;
        }

        if (logDirectory == null && dataDirectory != null) {
          logDirectory = dataDirectory + SEP + DATA_LOG_SUBDIRECTORY;
        }

        if (logPath == null && logDirectory != null) {
          logPath = logDirectory + SEP + logFileName;
        }
        if (logPathPattern == null && logDirectory != null) {
          logPathPattern = logDirectory + SEP + logFileNamePattern;
        }
      }
    }

    private void validateValues() {
      checkArgument(
          logPath != null,
          "LoggingConfig error: none of logPath, dataDirectory or logDirectory values was specified");
      checkArgument(
          logPathPattern != null,
          "LoggingConfig error: none of logPathPattern, dataDirectory or logDirectory values was specified");
      checkArgument(
          !(dbOpAlertThresholdMillis < 0),
          "LoggingConfig error: dbOpAlertThreshold must be a positive value");
    }

    public LoggingConfigBuilder logLevel(Level logLevel) {
      this.logLevel = Optional.ofNullable(logLevel);
      return this;
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

    public LoggingConfigBuilder dataDirectory(String dataDirectory) {
      this.dataDirectory = dataDirectory;
      return this;
    }

    public LoggingConfigBuilder logDirectory(String logDirectory) {
      this.logDirectory = logDirectory;
      return this;
    }

    public LoggingConfigBuilder logFileNamePrefix(String logFileNamePrefix) {
      this.logFileNamePrefix = logFileNamePrefix;
      return this;
    }

    public LoggingConfigBuilder logFileName(String logFileName) {
      this.logFileName = logFileName;
      return this;
    }

    public LoggingConfigBuilder logFileNamePattern(String logFileNamePattern) {
      this.logFileNamePattern = logFileNamePattern;
      return this;
    }

    public LoggingConfigBuilder logPath(String logPath) {
      this.logPath = logPath;
      return this;
    }

    public LoggingConfigBuilder logPathPattern(String logPathPattern) {
      this.logPathPattern = logPathPattern;
      return this;
    }

    public LoggingConfigBuilder dbOpAlertThresholdMillis(int dbOpAlertThresholdMillis) {
      this.dbOpAlertThresholdMillis = dbOpAlertThresholdMillis;
      return this;
    }

    public LoggingConfig build() {
      initMissingDefaults();
      validateValues();
      return new LoggingConfig(
          logLevel,
          colorEnabled,
          includeEventsEnabled,
          includeValidatorDutiesEnabled,
          includeP2pWarningsEnabled,
          destination,
          logPath,
          logPathPattern,
          dbOpAlertThresholdMillis);
    }
  }
}

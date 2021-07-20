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

    private boolean colorEnabled;
    private boolean includeEventsEnabled;
    private boolean includeValidatorDutiesEnabled;
    private boolean includeP2pWarningsEnabled;
    private LoggingDestination destination;
    private String logFile;
    private String logFileNamePattern;

    private LoggingConfigBuilder() {}

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

    public LoggingConfigBuilder logFile(String logFile) {
      this.logFile = logFile;
      return this;
    }

    public LoggingConfigBuilder logFileNamePattern(String logFileNamePattern) {
      this.logFileNamePattern = logFileNamePattern;
      return this;
    }

    public LoggingConfig build() {
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

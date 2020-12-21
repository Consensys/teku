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
  private final LoggingDestination destination;
  private final String logFile;
  private final String logFileNamePattern;
  private final boolean logWireCipher;
  private final boolean logWirePlain;
  private final boolean logWireMuxFrames;
  private final boolean logWireGossip;

  private LoggingConfig(
      final boolean colorEnabled,
      final boolean includeEventsEnabled,
      final boolean includeValidatorDutiesEnabled,
      final LoggingDestination destination,
      final String logFile,
      final String logFileNamePattern,
      final boolean logWireCipher,
      final boolean logWirePlain,
      final boolean logWireMuxFrames,
      final boolean logWireGossip) {
    this.colorEnabled = colorEnabled;
    this.includeEventsEnabled = includeEventsEnabled;
    this.includeValidatorDutiesEnabled = includeValidatorDutiesEnabled;
    this.destination = destination;
    this.logFile = logFile;
    this.logFileNamePattern = logFileNamePattern;
    this.logWireCipher = logWireCipher;
    this.logWirePlain = logWirePlain;
    this.logWireMuxFrames = logWireMuxFrames;
    this.logWireGossip = logWireGossip;
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

  public LoggingDestination getDestination() {
    return destination;
  }

  public String getLogFile() {
    return logFile;
  }

  public String getLogFileNamePattern() {
    return logFileNamePattern;
  }

  public boolean isLogWireCipher() {
    return logWireCipher;
  }

  public boolean isLogWirePlain() {
    return logWirePlain;
  }

  public boolean isLogWireMuxFrames() {
    return logWireMuxFrames;
  }

  public boolean isLogWireGossip() {
    return logWireGossip;
  }

  public static final class LoggingConfigBuilder {

    private boolean colorEnabled;
    private boolean includeEventsEnabled;
    private boolean includeValidatorDutiesEnabled;
    private LoggingDestination destination;
    private String logFile;
    private String logFileNamePattern;
    private boolean logWireCipher;
    private boolean logWirePlain;
    private boolean logWireMuxFrames;
    private boolean logWireGossip;

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

    public LoggingConfigBuilder logWireCipher(boolean logWireCipher) {
      this.logWireCipher = logWireCipher;
      return this;
    }

    public LoggingConfigBuilder logWirePlain(boolean logWirePlain) {
      this.logWirePlain = logWirePlain;
      return this;
    }

    public LoggingConfigBuilder logWireMuxFrames(boolean logWireMuxFrames) {
      this.logWireMuxFrames = logWireMuxFrames;
      return this;
    }

    public LoggingConfigBuilder logWireGossip(boolean logWireGossip) {
      this.logWireGossip = logWireGossip;
      return this;
    }

    public LoggingConfig build() {
      return new LoggingConfig(
          colorEnabled,
          includeEventsEnabled,
          includeValidatorDutiesEnabled,
          destination,
          logFile,
          logFileNamePattern,
          logWireCipher,
          logWirePlain,
          logWireMuxFrames,
          logWireGossip);
    }
  }
}

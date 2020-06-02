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

import tech.pegasys.teku.util.config.LoggingDestination;

public class LoggingConfiguration {

  private final boolean colorEnabled;
  private final boolean includeEventsEnabled;
  private final boolean includeValidatorDutiesEnabled;
  private final LoggingDestination destination;
  private final String file;
  private final String fileNamePattern;

  public LoggingConfiguration(
      final boolean colorEnabled,
      final boolean includeEventsEnabled,
      final boolean includeValidatorDutiesEnabled,
      final LoggingDestination destination,
      final String file,
      final String fileNamePattern) {
    this.colorEnabled = colorEnabled;
    this.includeEventsEnabled = includeEventsEnabled;
    this.includeValidatorDutiesEnabled = includeValidatorDutiesEnabled;
    this.destination = destination;
    this.file = file;
    this.fileNamePattern = fileNamePattern;
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

  public String getFile() {
    return file;
  }

  public String getFileNamePattern() {
    return fileNamePattern;
  }
}

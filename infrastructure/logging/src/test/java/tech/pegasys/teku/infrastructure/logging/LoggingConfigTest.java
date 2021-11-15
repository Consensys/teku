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

import static tech.pegasys.teku.infrastructure.logging.LoggingConfig.DEFAULT_LOG_DIRECTORY;
import static tech.pegasys.teku.infrastructure.logging.LoggingConfig.DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX;
import static tech.pegasys.teku.infrastructure.logging.LoggingConfig.DEFAULT_LOG_FILE_NAME_PREFIX;
import static tech.pegasys.teku.infrastructure.logging.LoggingConfig.DEFAULT_LOG_FILE_NAME_SUFFIX;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class LoggingConfigTest {

  private static final String FS = System.getProperty("file.separator");

  @Test
  public void shouldConstructDefaultPath() {
    LoggingConfig config = LoggingConfig.builder().build();

    Assertions.assertThat(config.getLogFile())
        .isEqualTo(
            DEFAULT_LOG_DIRECTORY
                + FS
                + DEFAULT_LOG_FILE_NAME_PREFIX
                + DEFAULT_LOG_FILE_NAME_SUFFIX);
    Assertions.assertThat(config.getLogFileNamePattern())
        .isEqualTo(
            DEFAULT_LOG_DIRECTORY
                + FS
                + DEFAULT_LOG_FILE_NAME_PREFIX
                + DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX);
  }

  @Test
  public void shouldConstructFromDataPath() {
    String dataDir = "." + FS + "mydata";
    LoggingConfig config = LoggingConfig.builder().dataDirectory(dataDir).build();

    Assertions.assertThat(config.getLogFile())
        .isEqualTo(
            dataDir
                + FS
                + "logs"
                + FS
                + DEFAULT_LOG_FILE_NAME_PREFIX
                + DEFAULT_LOG_FILE_NAME_SUFFIX);
    Assertions.assertThat(config.getLogFileNamePattern())
        .isEqualTo(
            dataDir
                + FS
                + "logs"
                + FS
                + DEFAULT_LOG_FILE_NAME_PREFIX
                + DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX);
  }

  @Test
  public void shouldConstructFromLogDirectory() {
    String logDir = "." + FS + "mylogs";
    LoggingConfig config = LoggingConfig.builder().logDirectory(logDir).build();

    Assertions.assertThat(config.getLogFile())
        .isEqualTo(logDir + FS + DEFAULT_LOG_FILE_NAME_PREFIX + DEFAULT_LOG_FILE_NAME_SUFFIX);
    Assertions.assertThat(config.getLogFileNamePattern())
        .isEqualTo(
            logDir + FS + DEFAULT_LOG_FILE_NAME_PREFIX + DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX);
  }

  @Test
  public void shouldConstructFromFilePrefix() {
    LoggingConfig config = LoggingConfig.builder().logFileNamePrefix("prefix").build();

    Assertions.assertThat(config.getLogFile())
        .isEqualTo(DEFAULT_LOG_DIRECTORY + FS + "prefix" + DEFAULT_LOG_FILE_NAME_SUFFIX);
    Assertions.assertThat(config.getLogFileNamePattern())
        .isEqualTo(DEFAULT_LOG_DIRECTORY + FS + "prefix" + DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX);
  }

  @Test
  public void shouldConstructWithFullPath() {
    String logDir = "." + FS + "mylogs";
    String logPath = logDir + FS + "happy.log";
    String logPathPattern = logDir + FS + "happy_%d{yyyy-MM-dd}.gzip";

    LoggingConfig config =
        LoggingConfig.builder()
            .logDirectory("ignore" + FS + "directory")
            .logFileNamePrefix("should.ignore")
            .logPath(logPath)
            .logPathPattern(logPathPattern)
            .build();

    Assertions.assertThat(config.getLogFile()).isEqualTo(logPath);
    Assertions.assertThat(config.getLogFileNamePattern()).isEqualTo(logPathPattern);
  }
}

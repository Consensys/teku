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

package tech.pegasys.teku.cli.util;

import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import tech.pegasys.teku.util.cli.VersionProvider;

public class LoggingPathBuilder {
  public static final String SEP = System.getProperty("file.separator");
  private String defaultPath = VersionProvider.defaultStoragePath();
  private Optional<String> dataPath = Optional.empty();
  private String fromCommandLine;
  private String defaultBasename;

  public LoggingPathBuilder defaultBasename(final String defaultBasename) {
    this.defaultBasename = defaultBasename;
    return this;
  }

  public LoggingPathBuilder dataPath(final Path dataPath) {
    this.dataPath = Optional.of(dataPath.toString());
    return this;
  }

  public LoggingPathBuilder fromCommandLine(final String path) {
    this.fromCommandLine = path;
    return this;
  }

  public String build() {
    if (StringUtils.isNotEmpty(fromCommandLine)) {
      if (fromCommandLine.contains(SEP)) {
        return fromCommandLine;
      } else {
        return StringUtils.joinWith(SEP, dataPath.orElse(defaultPath), "logs", fromCommandLine);
      }
    }

    return StringUtils.joinWith(SEP, dataPath.orElse(defaultPath), "logs", defaultBasename);
  }

  public LoggingPathBuilder maybeFromCommandLine(final Optional<String> maybeLogFile) {
    maybeLogFile.ifPresent(this::fromCommandLine);
    return this;
  }
}

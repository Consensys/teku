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

package tech.pegasys.teku.api.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.Optional;
import org.apache.logging.log4j.Level;

public class LogLevel {

  private final Level level;
  private String[] logFilter;

  @JsonCreator
  public LogLevel(@JsonProperty("level") final String level) {
    this.level = Level.valueOf(level);
  }

  @JsonSetter("log_filter")
  public void setLogFilter(final String[] logFilter) {
    this.logFilter = logFilter;
  }

  public Level getLevel() {
    return level;
  }

  @JsonGetter("level")
  public String getLevelAsString() {
    return level.toString();
  }

  @JsonGetter("log_filter")
  public Optional<String[]> getLogFilter() {
    return Optional.ofNullable(logFilter);
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.cli.util.LoggingPathBuilder.SEP;

import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.util.cli.VersionProvider;

public class LoggingPathBuilderTest {

  @Test
  public void shouldConstructDefaultPath() {
    LoggingPathBuilder builder = new LoggingPathBuilder().defaultBasename("teku.log");
    assertThat(builder.build())
        .isEqualTo(
            StringUtils.joinWith(SEP, VersionProvider.defaultStoragePath(), "logs", "teku.log"));
  }

  @Test
  public void shouldConstructDefaultPattern() {
    LoggingPathBuilder builder =
        new LoggingPathBuilder().defaultBasename("teku_%d{yyyy-MM-dd}.log");
    assertThat(builder.build())
        .isEqualTo(
            StringUtils.joinWith(
                SEP, VersionProvider.defaultStoragePath(), "logs", "teku_%d{yyyy-MM-dd}.log"));
  }

  @Test
  public void shouldConstructPathGivenCustomDataBaseDirectory() {
    LoggingPathBuilder builder =
        new LoggingPathBuilder().defaultBasename("t.log").dataPath(Path.of("/test"));
    assertThat(builder.build()).isEqualTo("/test/logs/t.log");
  }

  @Test
  public void shouldConstructPathGivenCustomFilename() {
    LoggingPathBuilder builder =
        new LoggingPathBuilder()
            .defaultBasename("t.log")
            .dataPath(Path.of("/t1"))
            .fromCommandLine("u.log");
    assertThat(builder.build()).isEqualTo("/t1/logs/u.log");
  }

  @Test
  public void shouldConstructPathGivenCustomAbsoluteFilename() {
    LoggingPathBuilder builder =
        new LoggingPathBuilder()
            .defaultBasename("t.log")
            .dataPath(Path.of("/t1"))
            .fromCommandLine("/u1/u.log");
    assertThat(builder.build()).isEqualTo("/u1/u.log");
  }

  @Test
  public void shouldConstructFromOnlyAbsoluteFilename() {
    LoggingPathBuilder builder = new LoggingPathBuilder().fromCommandLine("/u1/u.log");
    assertThat(builder.build()).isEqualTo("/u1/u.log");
  }

  @Test
  public void shouldConstructFromMaybeLogFile() {
    LoggingPathBuilder builder =
        new LoggingPathBuilder().maybeFromCommandLine(Optional.of("/u1/u.log"));
    assertThat(builder.build()).isEqualTo("/u1/u.log");
  }
}

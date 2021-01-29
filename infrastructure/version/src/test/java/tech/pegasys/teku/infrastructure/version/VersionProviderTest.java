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

package tech.pegasys.teku.infrastructure.version;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.version.VersionProvider.ENV_HOME;
import static tech.pegasys.teku.infrastructure.version.VersionProvider.ENV_LOCALAPPDATA;
import static tech.pegasys.teku.infrastructure.version.VersionProvider.ENV_XDG_DATA_HOME;

import java.util.Map;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.Test;

class VersionProviderTest {
  private final String TEKU = "/teku";

  @Test
  void defaultStoragePath_shouldHandleWindowsPath() {
    final String homeFolder = "c:\\users\\myUser\\AppData\\local";
    final Map<String, String> env = Map.of(ENV_LOCALAPPDATA, homeFolder);
    assertThat(VersionProvider.defaultStoragePathForNormalizedOS("windows", env))
        .isEqualTo(homeFolder + "\\teku");
  }

  @Test
  void defaultStoragePath_shouldHandleMacPath() {
    final String homeFolder = "/Users/myUser";
    Map<String, String> env = Map.of(ENV_HOME, homeFolder);
    assertThat(VersionProvider.defaultStoragePathForNormalizedOS("osx", env))
        .isEqualTo(homeFolder + "/Library" + TEKU);
  }

  @Test
  void defaultStoragePath_shouldHandleXdgHome() {
    final String homeFolder = "/data/myUser";
    Map<String, String> env = Map.of(ENV_XDG_DATA_HOME, homeFolder);
    assertThat(VersionProvider.defaultStoragePathForNormalizedOS("linux", env))
        .isEqualTo(homeFolder + TEKU);
  }

  @Test
  void defaultStoragePath_shouldHandleEmptyXdgHome() {
    final String homeFolder = "/home/myUser";
    Map<String, String> env = Map.of(ENV_XDG_DATA_HOME, Strings.EMPTY, ENV_HOME, homeFolder);
    assertThat(VersionProvider.defaultStoragePathForNormalizedOS("aix", env))
        .isEqualTo(homeFolder + "/.local/share" + TEKU);
  }

  @Test
  void defaultStoragePath_shouldHandleLocalHome() {
    final String homeFolder = "/home/myUser";
    Map<String, String> env = Map.of(ENV_HOME, homeFolder);
    assertThat(VersionProvider.defaultStoragePathForNormalizedOS("aix", env))
        .isEqualTo(homeFolder + "/.local/share" + TEKU);
  }
}

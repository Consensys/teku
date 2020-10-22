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

package tech.pegasys.teku.storage.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

public class DatabaseVersionTest {
  @Test
  public void defaultVersion() {
    assertThat(DatabaseVersion.DEFAULT_VERSION).isEqualTo(DatabaseVersion.V5);
  }

  @Test
  public void shouldAcceptV6FromString() {
    Optional<DatabaseVersion> data = DatabaseVersion.fromString("6");
    assertThat(data).contains(DatabaseVersion.V6);
  }

  @Test
  public void shouldAcceptV5FromString() {
    Optional<DatabaseVersion> data = DatabaseVersion.fromString("5");
    assertThat(data).contains(DatabaseVersion.V5);
  }

  @Test
  public void shouldAcceptV4FromString() {
    Optional<DatabaseVersion> data = DatabaseVersion.fromString("4");
    assertThat(data).contains(DatabaseVersion.V4);
  }
}

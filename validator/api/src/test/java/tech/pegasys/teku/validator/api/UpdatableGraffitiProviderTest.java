/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.validator.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class UpdatableGraffitiProviderTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final Bytes32 storageGraffiti = dataStructureUtil.randomBytes32();
  private final Bytes32 defaultGraffiti = dataStructureUtil.randomBytes32();
  private UpdatableGraffitiProvider provider;

  @Test
  void shouldGetStorageGraffitiWhenAvailable() {
    provider = new UpdatableGraffitiProvider(() -> Optional.of(storageGraffiti), Optional::empty);
    assertThat(provider.get()).hasValue(storageGraffiti);
  }

  @Test
  void shouldGetStorageGraffitiWhenBothAvailable() {
    provider =
        new UpdatableGraffitiProvider(
            () -> Optional.of(storageGraffiti), () -> Optional.of(defaultGraffiti));
    assertThat(provider.get()).hasValue(storageGraffiti);
  }

  @Test
  void shouldGetDefaultGraffitiWhenStorageEmpty() {
    provider = new UpdatableGraffitiProvider(Optional::empty, () -> Optional.of(defaultGraffiti));
    assertThat(provider.get()).hasValue(defaultGraffiti);
  }

  @Test
  void shouldBeEmptyWhenBothEmpty() {
    provider = new UpdatableGraffitiProvider(Optional::empty, Optional::empty);
    assertThat(provider.get()).isEmpty();
  }
}

/*
 * Copyright Consensys Software Inc., 2023
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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static tech.pegasys.teku.storage.server.StateStorageMode.ARCHIVE;
import static tech.pegasys.teku.storage.server.StateStorageMode.MINIMAL;
import static tech.pegasys.teku.storage.server.StateStorageMode.NOT_SET;
import static tech.pegasys.teku.storage.server.StateStorageMode.PRUNE;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class StorageConfigurationTest {

  public static Stream<Arguments> getStateStorageDefaultScenarios() {
    ArrayList<Arguments> args = new ArrayList<>();
    args.add(Arguments.of(false, Optional.empty(), NOT_SET, MINIMAL));
    args.add(Arguments.of(false, Optional.empty(), ARCHIVE, ARCHIVE));
    args.add(Arguments.of(false, Optional.empty(), MINIMAL, MINIMAL));
    args.add(Arguments.of(false, Optional.empty(), PRUNE, PRUNE));
    args.add(Arguments.of(true, Optional.empty(), NOT_SET, PRUNE));
    args.add(Arguments.of(true, Optional.of(ARCHIVE), NOT_SET, ARCHIVE));
    args.add(Arguments.of(true, Optional.of(PRUNE), StateStorageMode.ARCHIVE, ARCHIVE));
    args.add(Arguments.of(true, Optional.of(ARCHIVE), MINIMAL, MINIMAL));
    return args.stream();
  }

  // a previously used version that is recorded will be used if the requested mode is NOT_SET.
  // if a mode is specified, it should be used, regardless of what was previously used
  // not set has 2 meanings - basically there's a smart default taking place to handle the old
  // default of prune, if the data-storage is already present
  @ParameterizedTest
  @MethodSource("getStateStorageDefaultScenarios")
  void shouldDetermineCorrectStorageModeGivenInputs(
      final boolean isExistingStore,
      final Optional<StateStorageMode> maybePreviousStorageMode,
      final StateStorageMode requestedMode,
      final StateStorageMode expectedResult) {

    assertThat(
            StorageConfiguration.determineStorageDefault(
                isExistingStore, maybePreviousStorageMode, requestedMode))
        .isEqualTo(expectedResult);
  }
}

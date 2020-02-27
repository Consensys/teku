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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.artemis.beaconrestapi.RestApiUtils.validateQueryParameter;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class RestApiUtilsTest {

  public static final String KEY = "any";
  public static final String VALUE = "1";

  @Test
  public void validateParameters_shouldDetectMissingKey() {
    Map<String, List<String>> data = Map.of();

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          validateQueryParameter(data, KEY);
        });
  }

  @Test
  public void validateParameters_shouldDetectEmptyString() {
    Map<String, List<String>> data = Map.of(KEY, List.of());

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          validateQueryParameter(data, KEY);
        });
  }

  @Test
  public void validateParameters_shouldDetectMultipleEntries() {
    Map<String, List<String>> data = Map.of(KEY, List.of("1", "2"));

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          validateQueryParameter(data, KEY);
        });
  }

  @Test
  public void validateParameters_shouldReturnValue() {
    Map<String, List<String>> data = Map.of(KEY, List.of(VALUE));

    assertEquals(VALUE, validateQueryParameter(data, KEY));
  }
}

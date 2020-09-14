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

package tech.pegasys.teku.beaconrestapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ListQueryParameterUtilsTest {
  @Test
  public void integerList_shouldHandleMultipleIndividualEntries() {
    final Map<String, List<String>> data = Map.of("index", List.of("1", "2", "3"));
    assertThat(ListQueryParameterUtils.getParameterAsIntegerList(data, "index"))
        .isEqualTo(List.of(1, 2, 3));
  }

  @Test
  public void integerList_shouldHandleCompoundEntries() {
    final Map<String, List<String>> data = Map.of("index", List.of("1,2,3", "4,5,6", "7"));
    assertThat(ListQueryParameterUtils.getParameterAsIntegerList(data, "index"))
        .isEqualTo(List.of(1, 2, 3, 4, 5, 6, 7));
  }

  @Test
  public void integerList_shouldHandleSingleEntry() {
    final Map<String, List<String>> data = Map.of("index", List.of("1"));
    assertThat(ListQueryParameterUtils.getParameterAsIntegerList(data, "index"))
        .isEqualTo(List.of(1));
  }

  @Test
  public void integerList_shouldTolerateAMissingEntry() {
    final Map<String, List<String>> data = Map.of("index", List.of("1,,2", "", "3,4,5,"));
    assertThat(ListQueryParameterUtils.getParameterAsIntegerList(data, "index"))
        .isEqualTo(List.of(1, 2, 3, 4, 5));
  }
}

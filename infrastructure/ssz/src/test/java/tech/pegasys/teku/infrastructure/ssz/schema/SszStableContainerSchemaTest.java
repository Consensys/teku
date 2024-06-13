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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static tech.pegasys.teku.infrastructure.ssz.TestStableContainers.CIRCLE_PROFILE_SCHEMA;
import static tech.pegasys.teku.infrastructure.ssz.TestStableContainers.CIRCLE_STABLE_CONTAINER_SCHEMA;
import static tech.pegasys.teku.infrastructure.ssz.TestStableContainers.SQUARE_PROFILE_SCHEMA;
import static tech.pegasys.teku.infrastructure.ssz.TestStableContainers.SQUARE_STABLE_CONTAINER_SCHEMA;

import java.util.stream.Stream;

public class SszStableContainerSchemaTest extends SszCompositeSchemaTestBase {

  public static Stream<SszContainerSchema<?>> testContainerSchemas() {
    return Stream.of(
        SQUARE_STABLE_CONTAINER_SCHEMA,
        CIRCLE_STABLE_CONTAINER_SCHEMA,
        SQUARE_PROFILE_SCHEMA,
        CIRCLE_PROFILE_SCHEMA);
  }

  @Override
  public Stream<SszContainerSchema<?>> testSchemas() {
    return testContainerSchemas();
  }
}

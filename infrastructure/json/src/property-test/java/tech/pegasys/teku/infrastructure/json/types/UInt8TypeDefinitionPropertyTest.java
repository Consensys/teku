/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.json.types;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.json.DeserializableTypeUtil.assertRoundTrip;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class UInt8TypeDefinitionPropertyTest {
  @Property
  void roundTrip(@ForAll Byte value) throws JsonProcessingException {
    assertRoundTrip(value, CoreTypes.UINT8_TYPE);
  }

  @Property
  @SuppressWarnings("EmptyCatch")
  void shouldRejectInvalidRange(@ForAll int value) {
    try {
      final String serialized = Integer.toUnsignedString(value, 10);
      JsonUtil.parse(serialized, CoreTypes.UINT8_TYPE);
      assertThat(value).isBetween(-127, 255);
    } catch (JsonProcessingException e) {
    } catch (IllegalArgumentException e) {
    }
  }
}

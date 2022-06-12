/*
 * Copyright ConsenSys Software Inc., 2022
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBLSSignature;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SingleQueryParameterUtilsTest {

  public static final String KEY = "any";
  public static final String VALUE = "1";
  public static final Map<String, List<String>> INVALID_DATA = Map.of(KEY, List.of("1.5"));

  @Test
  public void getParameterAsBLSSignature_shouldThrowIfCannotParse() {
    assertThrows(
        IllegalArgumentException.class, () -> getParameterValueAsBLSSignature(INVALID_DATA, KEY));
  }

  @Test
  public void getParameterAsBLSSignature_shouldParseBytes96Data() {
    BLSSignature signature = new BLSSignature(Bytes.random(96));
    Map<String, List<String>> data = Map.of(KEY, List.of(signature.toHexString()));
    BLSSignature result = getParameterValueAsBLSSignature(data, KEY);
    assertEquals(signature, result);
  }

  @Test
  public void getParameterAsUInt64IfPresent_shouldReturnEmptyIfNotPresent() {
    assertThat(SingleQueryParameterUtils.getParameterValueAsUInt64IfPresent(Map.of(), "t"))
        .isEmpty();
  }

  @Test
  public void getParameterAsUInt64IfPresent_shouldReturnData() {
    final UInt64 value = UInt64.valueOf("123456");
    assertThat(
            SingleQueryParameterUtils.getParameterValueAsUInt64IfPresent(
                Map.of("t", List.of(value.toString())), "t"))
        .isEqualTo(Optional.of(value));
  }
}

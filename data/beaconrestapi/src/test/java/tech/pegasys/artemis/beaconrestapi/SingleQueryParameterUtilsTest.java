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

package tech.pegasys.artemis.beaconrestapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBLSSignature;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBytes32;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsInt;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsLong;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUnsignedLong;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.validateQueryParameter;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.api.schema.BLSSignature;

public class SingleQueryParameterUtilsTest {

  public static final String KEY = "any";
  public static final String VALUE = "1";
  public static final Map<String, List<String>> INVALID_DATA = Map.of(KEY, List.of("1.5"));

  @Test
  public void validateParameters_shouldDetectMissingKey() {
    Map<String, List<String>> data = Map.of();

    assertThrows(IllegalArgumentException.class, () -> validateQueryParameter(data, KEY));
  }

  @Test
  public void validateParameters_shouldDetectEmptyString() {
    Map<String, List<String>> data = Map.of(KEY, List.of());

    assertThrows(IllegalArgumentException.class, () -> validateQueryParameter(data, KEY));
  }

  @Test
  public void validateParameters_shouldDetectMultipleEntries() {
    Map<String, List<String>> data = Map.of(KEY, List.of("1", "2"));

    assertThrows(IllegalArgumentException.class, () -> validateQueryParameter(data, KEY));
  }

  @Test
  public void validateParameters_shouldReturnValue() {
    Map<String, List<String>> data = Map.of(KEY, List.of(VALUE));

    assertEquals(VALUE, validateQueryParameter(data, KEY));
  }

  @Test
  public void getParameterValueAsInt_shouldReturnValue() {
    Map<String, List<String>> data = Map.of(KEY, List.of(VALUE));
    assertEquals(1, getParameterValueAsInt(data, KEY));
  }

  @Test
  public void getParameterValueAsInt_shouldThrowIllegalArgIfNotIntValue_String() {
    Map<String, List<String>> data = Map.of(KEY, List.of("not-an-int"));
    assertThrows(IllegalArgumentException.class, () -> getParameterValueAsInt(data, KEY));
  }

  @Test
  public void getParameterValueAsInt_shouldThrowIllegalArgIfNotIntValue_Decimal() {
    assertThrows(IllegalArgumentException.class, () -> getParameterValueAsInt(INVALID_DATA, KEY));
  }

  @Test
  public void getParameterAsUnsignedLong_shouldReturnValue() {
    Map<String, List<String>> data = Map.of(KEY, List.of("1"));
    UnsignedLong result = getParameterValueAsUnsignedLong(data, KEY);
    assertEquals(UnsignedLong.ONE, result);
  }

  @Test
  public void getParameterAsUnsignedLong_shouldThrowIfCannotParse() {
    assertThrows(
        IllegalArgumentException.class, () -> getParameterValueAsUnsignedLong(INVALID_DATA, KEY));
  }

  @Test
  public void getParameterAsLong_shouldReturnValue() {
    Map<String, List<String>> data = Map.of(KEY, List.of("1"));
    long result = getParameterValueAsLong(data, KEY);
    assertEquals(1L, result);
  }

  @Test
  public void getParameterAsLong_shouldThrowIfCannotParse() {
    assertThrows(IllegalArgumentException.class, () -> getParameterValueAsLong(INVALID_DATA, KEY));
  }

  @Test
  public void getParameterAsBytes32_shouldThrowIfCannotParse() {
    assertThrows(
        IllegalArgumentException.class, () -> getParameterValueAsBytes32(INVALID_DATA, KEY));
  }

  @Test
  public void getParameterAsBytes32_shouldParseHex32String() {
    Bytes32 bytes32 = Bytes32.random();
    Map<String, List<String>> data = Map.of(KEY, List.of(bytes32.toHexString()));
    Bytes32 result = getParameterValueAsBytes32(data, KEY);
    assertEquals(bytes32, result);
  }

  @Test
  public void getParameterAsBLSSignature_shouldThrowIfCannotParse() {
    assertThrows(
        IllegalArgumentException.class, () -> getParameterValueAsBytes32(INVALID_DATA, KEY));
  }

  @Test
  public void getParameterAsBLSSignature_shouldParseBytes96Data() {
    BLSSignature signature = new BLSSignature(Bytes.random(96));
    Map<String, List<String>> data = Map.of(KEY, List.of(signature.toHexString()));
    BLSSignature result = getParameterValueAsBLSSignature(data, KEY);
    assertEquals(signature, result);
  }
}

/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

public class ValidatorKeysOptionsTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final String firstKeyStr =
      dataStructureUtil.randomPublicKey().toBytesCompressed().toHexString();
  private final String secondKeyStr =
      dataStructureUtil.randomPublicKey().toBytesCompressed().toHexString();
  private final String urlSource = "http://my.host";
  private final BLSPublicKey firstKey =
      BLSPublicKey.fromBytesCompressed(Bytes48.fromHexString(firstKeyStr));
  private final BLSPublicKey secondKey =
      BLSPublicKey.fromBytesCompressed(Bytes48.fromHexString(secondKeyStr));

  private final ObjectMapper mapper = mock(ObjectMapper.class);
  private final ValidatorKeysOptions.PublicKeyLoader loader =
      new ValidatorKeysOptions.PublicKeyLoader(mapper);

  @Test
  void shouldGetListOfLocallySpecifiedPubKeys() {
    assertThat(loader.getPublicKeys(List.of(firstKeyStr, secondKeyStr)))
        .containsExactly(firstKey, secondKey);
  }

  @Test
  void shouldRemoveDuplicateKeysFromLocalList() {
    assertThat(loader.getPublicKeys(List.of(firstKeyStr, secondKeyStr, firstKeyStr)))
        .containsExactly(firstKey, secondKey);
  }

  @Test
  void shouldReadFromUrl() throws IOException {
    final String[] values = {firstKeyStr, secondKeyStr};
    when(mapper.readValue(new URL(urlSource), String[].class)).thenReturn(values);
    assertThat(loader.getPublicKeys(List.of(urlSource))).containsExactly(firstKey, secondKey);
  }

  @Test
  void shouldHandleDuplicatesAcrossSources() throws IOException {
    final String[] values = {firstKeyStr, secondKeyStr};
    when(mapper.readValue(new URL(urlSource), String[].class)).thenReturn(values);
    assertThat(loader.getPublicKeys(List.of(firstKeyStr, urlSource, secondKeyStr)))
        .containsExactly(firstKey, secondKey);
  }

  @Test
  void shouldHandleEmptyResponseFromUrl() throws IOException {
    final String[] values = {};
    when(mapper.readValue(new URL(urlSource), String[].class)).thenReturn(values);
    assertThat(loader.getPublicKeys(List.of(urlSource, secondKeyStr))).containsExactly(secondKey);
  }
}

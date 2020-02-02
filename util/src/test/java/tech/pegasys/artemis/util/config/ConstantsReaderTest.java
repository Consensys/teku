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

package tech.pegasys.artemis.util.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConstantsReaderTest {
  private static final List<String> ZERO_FIELDS = List.of("GENESIS_SLOT", "GENESIS_EPOCH");

  @AfterEach
  public void tearDown() {
    Constants.setConstants("minimal");
  }

  @Test
  public void shouldLoadConstants() throws Exception {
    final String config =
        "MAX_COMMITTEES_PER_SLOT: 68\n" + "# 2**7 (= 128)\n" + "TARGET_COMMITTEE_SIZE: 129";
    ConstantsReader.loadConstantsFrom(
        new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8)));

    // Sanity check a couple of values
    assertThat(Constants.MAX_COMMITTEES_PER_SLOT).isEqualTo(68);
    assertThat(Constants.TARGET_COMMITTEE_SIZE).isEqualTo(129);
  }

  @Test
  public void shouldLoadMainnetConstants() throws Exception {
    Constants.setConstants("mainnet");

    // Sanity check a couple of values
    assertThat(Constants.MAX_COMMITTEES_PER_SLOT).isEqualTo(64);
    assertThat(Constants.TARGET_COMMITTEE_SIZE).isEqualTo(128);
    assertAllFieldsSet();
  }

  @Test
  public void shouldLoadMinimalConstants() throws Exception {
    Constants.setConstants("minimal");

    assertThat(Constants.MAX_COMMITTEES_PER_SLOT).isEqualTo(4);
    assertThat(Constants.TARGET_COMMITTEE_SIZE).isEqualTo(4);
    assertAllFieldsSet();
  }

  @Test
  public void shouldLoadFromUrl() throws Exception {
    Constants.setConstants(Constants.class.getResource("mainnet.yaml").toExternalForm());
    assertThat(Constants.TARGET_COMMITTEE_SIZE).isEqualTo(128);
    assertAllFieldsSet();
  }

  private void assertAllFieldsSet() throws Exception {
    for (Field field : Constants.class.getFields()) {
      final Object value = field.get(null);
      assertThat(value).describedAs(field.getName()).isNotNull();
      if (!ZERO_FIELDS.contains(field.getName())) {
        assertThat(value).describedAs(field.getName()).isNotEqualTo(0);
        assertThat(value).describedAs(field.getName()).isNotEqualTo(0L);
        assertThat(value).describedAs(field.getName()).isNotEqualTo(UnsignedLong.ZERO);
      }
    }
  }
}

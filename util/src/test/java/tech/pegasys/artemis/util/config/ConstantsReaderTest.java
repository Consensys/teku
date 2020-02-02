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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConstantsReaderTest {

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
  public void shouldLoadMainnetConstants() {
    Constants.setConstants("mainnet");

    // Sanity check a couple of values
    assertThat(Constants.MAX_COMMITTEES_PER_SLOT).isEqualTo(64);
    assertThat(Constants.TARGET_COMMITTEE_SIZE).isEqualTo(128);
  }

  @Test
  public void shouldLoadMinimalConstants() {
    Constants.setConstants("minimal");

    assertThat(Constants.MAX_COMMITTEES_PER_SLOT).isEqualTo(4);
    assertThat(Constants.TARGET_COMMITTEE_SIZE).isEqualTo(4);
  }

  @Test
  public void shouldLoadFromUrl() {
    Constants.setConstants(
        "https://github.com/eth2-clients/eth2-testnets/raw/master/prysm/Sapphire(v0.9.4)/config.yaml");
    assertThat(Constants.TARGET_COMMITTEE_SIZE).isEqualTo(128);
    assertThat(Constants.JUSTIFICATION_BITS_LENGTH).isEqualTo(4);
  }
}

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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class RuntimeProposerConfigTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private RuntimeProposerConfig proposerConfig = new RuntimeProposerConfig(Optional.empty());
  private final BLSPublicKey pubkey = dataStructureUtil.randomPublicKey();
  private final BLSPublicKey pubkey2 = dataStructureUtil.randomPublicKey();
  private final Eth1Address address = dataStructureUtil.randomEth1Address();
  private final Eth1Address address2 = dataStructureUtil.randomEth1Address();

  @Test
  void shouldAddEntries() {
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).isEmpty();
    proposerConfig.addOrUpdate(pubkey, address);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address);
  }

  @Test
  void shouldUpdateEntries() {
    proposerConfig.addOrUpdate(pubkey, address);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address);
    proposerConfig.addOrUpdate(pubkey, address2);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address2);
  }

  @Test
  void shouldDeleteEntries() {
    proposerConfig.addOrUpdate(pubkey, address);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address);
    proposerConfig.delete(pubkey);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).isEmpty();
  }

  @Test
  void shouldSave(@TempDir final Path tempDir) throws IOException {
    final Path testData = tempDir.resolve("test");
    proposerConfig = new RuntimeProposerConfig(Optional.of(testData));
    proposerConfig.addOrUpdate(pubkey, address);
    proposerConfig.addOrUpdate(pubkey2, address2);
    final String data = Files.readString(testData);
    assertThat(data)
        .isEqualTo(
            String.format(
                "{\"%s\":{\"fee_recipient\":\"%s\"},\"%s\":{\"fee_recipient\":\"%s\"}}",
                pubkey, address, pubkey2, address2));
  }

  @Test
  void shouldLoadOnCreation(@TempDir final Path tempDir) throws IOException {
    final Path testData = tempDir.resolve("test");
    Files.writeString(
        testData,
        String.format(
            "{\"%s\":{\"fee_recipient\":\"%s\"},\"%s\":{\"fee_recipient\":\"%s\"}}",
            pubkey, address, pubkey2, address2),
        StandardCharsets.UTF_8);
    proposerConfig = new RuntimeProposerConfig(Optional.of(testData));
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey2)).contains(address2);
  }
}

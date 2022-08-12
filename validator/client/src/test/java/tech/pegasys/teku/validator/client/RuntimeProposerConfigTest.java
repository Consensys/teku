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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
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
  private final UInt64 gasLimit = dataStructureUtil.randomUInt64();
  private final UInt64 gasLimit2 = dataStructureUtil.randomUInt64();

  @Test
  void shouldAddFeeRecipientEntries() {
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).isEmpty();
    proposerConfig.addOrUpdateFeeRecipient(pubkey, address);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address);
  }

  @Test
  void shouldAddGasLimitEntries() {
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).isEmpty();
    proposerConfig.addOrUpdateGasLimit(pubkey, gasLimit);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).contains(gasLimit);
  }

  @Test
  void shouldUpdateFeeRecipientEntries() {
    proposerConfig.addOrUpdateFeeRecipient(pubkey, address);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address);
    proposerConfig.addOrUpdateFeeRecipient(pubkey, address2);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address2);
  }

  @Test
  void shouldUpdateGasLimitEntries() {
    proposerConfig.addOrUpdateGasLimit(pubkey, gasLimit);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).contains(gasLimit);
    proposerConfig.addOrUpdateGasLimit(pubkey, gasLimit2);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).contains(gasLimit2);
  }

  @Test
  void shouldDeleteFeeRecipientEntries() {
    proposerConfig.addOrUpdateFeeRecipient(pubkey, address);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address);
    proposerConfig.deleteFeeRecipient(pubkey);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).isEmpty();
  }

  @Test
  void shouldDeleteGasLimitEntries() {
    proposerConfig.addOrUpdateGasLimit(pubkey, gasLimit);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).contains(gasLimit);
    proposerConfig.deleteGasLimit(pubkey);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).isEmpty();
  }

  @Test
  void shouldSave(@TempDir final Path tempDir) throws IOException {
    final Path testData = tempDir.resolve("test");
    proposerConfig = new RuntimeProposerConfig(Optional.of(testData));
    proposerConfig.addOrUpdateFeeRecipient(pubkey, address);
    proposerConfig.addOrUpdateGasLimit(pubkey, gasLimit);
    proposerConfig.addOrUpdateFeeRecipient(pubkey2, address2);
    proposerConfig.addOrUpdateGasLimit(pubkey2, gasLimit2);
    final String data = Files.readString(testData);
    assertThat(data)
        .isEqualTo(
            String.format(
                "{\"%s\":{\"fee_recipient\":\"%s\",\"gas_limit\":\"%s\"},\"%s\":{\"fee_recipient\":\"%s\",\"gas_limit\":\"%s\"}}",
                pubkey, address, gasLimit, pubkey2, address2, gasLimit2));
  }

  @Test
  void shouldLoadOnCreation(@TempDir final Path tempDir) throws IOException {
    final Path testData = tempDir.resolve("test");
    Files.writeString(
        testData,
        String.format(
            "{\"%s\":{\"fee_recipient\":\"%s\",\"gas_limit\":\"%s\"},\"%s\":{\"fee_recipient\":\"%s\",\"gas_limit\":\"%s\"}}",
            pubkey, address, gasLimit, pubkey2, address2, gasLimit2),
        StandardCharsets.UTF_8);
    proposerConfig = new RuntimeProposerConfig(Optional.of(testData));
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).contains(gasLimit);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey2)).contains(address2);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey2)).contains(gasLimit2);
  }
}

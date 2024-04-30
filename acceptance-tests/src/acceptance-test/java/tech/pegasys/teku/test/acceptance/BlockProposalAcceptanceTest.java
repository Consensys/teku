/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.test.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.eip7594.SignedBeaconBlockEip7594;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class BlockProposalAcceptanceTest extends AcceptanceTestBase {
  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");

  @Test
  void shouldHaveCorrectFeeRecipientAndGraffiti() throws Exception {
    final String networkName = "swift";

    final ValidatorKeystores validatorKeystores =
        createTekuDepositSender(networkName).generateValidatorKeys(8);

    final InitialStateData genesis =
        createGenesisGenerator()
            .network(networkName)
            .withAltairEpoch(UInt64.ZERO)
            .withBellatrixEpoch(UInt64.ONE)
            .withCapellaEpoch(UInt64.valueOf(2))
            .withDenebEpoch(UInt64.valueOf(3))
            .withEip7594Epoch(UInt64.valueOf(4))
            .validatorKeys(validatorKeystores, validatorKeystores)
            .generate();

    final String defaultFeeRecipient = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";
    final String userGraffiti = "My block \uD83D\uDE80"; // 13 bytes
    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withStubExecutionEngine()
                .withJwtSecretFile(JWT_FILE)
                .withNetwork(networkName)
                .withInitialState(genesis)
                .withRealNetwork()
                .withAltairEpoch(UInt64.ZERO)
                .withBellatrixEpoch(UInt64.ONE)
                .withCapellaEpoch(UInt64.valueOf(2))
                .withDenebEpoch(UInt64.valueOf(3))
                .withEip7594Epoch(UInt64.valueOf(4))
                .withTotalTerminalDifficulty(0)
                .withTrustedSetupFromClasspath("mainnet-trusted-setup.txt")
                .withValidatorProposerDefaultFeeRecipient(defaultFeeRecipient)
                .build());
    final TekuValidatorNode validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withReadOnlyKeystorePath(validatorKeystores)
                .withValidatorProposerDefaultFeeRecipient(defaultFeeRecipient)
                .withInteropModeDisabled()
                .withBeaconNodes(beaconNode)
                .withGraffiti(userGraffiti)
                .withNetwork("auto")
                .build());

    beaconNode.start();
    validatorClient.start();

    beaconNode.waitForEpochAtOrAbove(4);
    beaconNode.waitForBlockSatisfying(
        block -> {
          assertThat(block).isInstanceOf(SignedBeaconBlockEip7594.class);
          final SignedBeaconBlockEip7594 eip7594Block = (SignedBeaconBlockEip7594) block;
          assertThat(
                  eip7594Block.getMessage().getBody().executionPayload.feeRecipient.toHexString())
              .isEqualTo(defaultFeeRecipient.toLowerCase(Locale.ROOT));
          final Bytes32 graffiti = eip7594Block.getMessage().getBody().graffiti;
          final String graffitiMessage =
              new String(
                  Arrays.copyOfRange(
                      graffiti.toArray(), 0, 32 - graffiti.numberOfTrailingZeroBytes()),
                  StandardCharsets.UTF_8);
          // 13 bytes + 1 byte
          assertThat(graffitiMessage).startsWith(userGraffiti + " ");
          // 18 bytes left, so 12 bytes client footprint: TKxxxxELxxxx. 20 bytes with full commits
          // doesn't fit
          assertThat(graffitiMessage).contains("TK");
          // stub execution endpoint.
          assertThat(graffitiMessage).endsWith("SB0000");
        });
  }
}

/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.reference.fulu.networking;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.BlsSetting.IGNORED;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;

public class GossipVoluntaryExitTest implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final GossipVoluntaryExitMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipVoluntaryExitMetaData.class);
    final Spec spec = testDefinition.getSpec();
    final BeaconState state = loadStateFromSsz(testDefinition, "state.ssz_snappy");
    final BLSSignatureVerifier signatureVerifier =
        metaData.getBlsSetting() == IGNORED
            ? BLSSignatureVerifier.NOOP
            : BLSSignatureVerifier.SIMPLE;

    final Set<UInt64> seenValidators = new HashSet<>();

    for (final GossipVoluntaryExitMetaData.Message message : metaData.getMessages()) {
      final SignedVoluntaryExit exit =
          loadSsz(
              testDefinition, message.getMessage() + ".ssz_snappy", SignedVoluntaryExit.SSZ_SCHEMA);
      final UInt64 validatorIndex = exit.getMessage().getValidatorIndex();

      if (seenValidators.contains(validatorIndex)) {
        assertThat(message.getExpected())
            .describedAs("Expected ignore for already-seen validator %s", validatorIndex)
            .isEqualTo("ignore");
      } else {
        final Optional<OperationInvalidReason> invalidReason =
            spec.validateVoluntaryExit(state, exit);
        final boolean signatureValid =
            invalidReason.isEmpty()
                && spec.verifyVoluntaryExitSignature(state, exit, signatureVerifier);
        final boolean rejected = invalidReason.isPresent() || !signatureValid;

        switch (message.getExpected()) {
          case "valid" -> {
            assertThat(invalidReason)
                .describedAs("Expected valid exit for validator %s", validatorIndex)
                .isEmpty();
            assertThat(signatureValid)
                .describedAs("Expected valid signature for validator %s", validatorIndex)
                .isTrue();
            seenValidators.add(validatorIndex);
          }
          case "reject" ->
              assertThat(rejected)
                  .describedAs("Expected reject for validator %s", validatorIndex)
                  .isTrue();
          default ->
              throw new AssertionError(
                  "Unexpected expected value: " + message.getExpected() + " for unseen validator");
        }
      }
    }
  }

  @SuppressWarnings("unused")
  private static class GossipVoluntaryExitMetaData {

    @JsonProperty(value = "topic", required = true)
    private String topic;

    @JsonProperty(value = "messages", required = true)
    private List<Message> messages;

    @JsonProperty(value = "bls_setting", required = false, defaultValue = "0")
    private int blsSetting;

    public List<Message> getMessages() {
      return messages;
    }

    public BlsSetting getBlsSetting() {
      return BlsSetting.forCode(blsSetting);
    }

    public String getTopic() {
      return topic;
    }

    private static class Message {

      @JsonProperty(value = "message", required = true)
      private String message;

      @JsonProperty(value = "expected", required = true)
      private String expected;

      @JsonProperty(value = "reason", required = false)
      private String reason;

      public String getMessage() {
        return message;
      }

      public String getExpected() {
        return expected;
      }

      public String getReason() {
        return reason;
      }
    }
  }
}

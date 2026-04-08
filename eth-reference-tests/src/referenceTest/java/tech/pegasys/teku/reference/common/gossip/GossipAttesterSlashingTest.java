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

package tech.pegasys.teku.reference.common.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;

public class GossipAttesterSlashingTest implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final GossipAttesterSlashingMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipAttesterSlashingMetaData.class);
    final Spec spec = testDefinition.getSpec();
    final BeaconState state = loadStateFromSsz(testDefinition, "state.ssz_snappy");

    // Track seen slashable validator indices across messages
    final Set<UInt64> seenIndices = new HashSet<>();

    for (final GossipAttesterSlashingMetaData.Message message : metaData.getMessages()) {
      final AttesterSlashing slashing =
          loadSsz(
              testDefinition,
              message.getMessage() + ".ssz_snappy",
              spec.getGenesisSchemaDefinitions().getAttesterSlashingSchema());

      final Set<UInt64> intersectingIndices = slashing.getIntersectingValidatorIndices();

      if (seenIndices.containsAll(intersectingIndices)) {
        // All intersecting indices already seen (includes empty intersection case)
        assertThat(message.getExpected())
            .describedAs(
                "Expected ignore for attester slashing %s (all indices already seen)",
                message.getMessage())
            .isEqualTo("ignore");
        continue;
      }

      final Optional<OperationInvalidReason> invalidReason =
          spec.validateAttesterSlashing(state, slashing);

      if (invalidReason.isPresent()) {
        assertThat(message.getExpected())
            .describedAs(
                "Expected reject for attester slashing %s: %s",
                message.getMessage(), invalidReason.get().describe())
            .isEqualTo("reject");
      } else {
        assertThat(message.getExpected())
            .describedAs("Expected valid for attester slashing %s", message.getMessage())
            .isEqualTo("valid");
        seenIndices.addAll(intersectingIndices);
      }
    }
  }

  @SuppressWarnings("unused")
  private static class GossipAttesterSlashingMetaData {

    @JsonProperty(value = "topic", required = true)
    private String topic;

    @JsonProperty(value = "messages", required = true)
    private List<Message> messages;

    @JsonProperty(value = "bls_setting", required = false, defaultValue = "0")
    private int blsSetting;

    public List<Message> getMessages() {
      return messages;
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
    }
  }
}

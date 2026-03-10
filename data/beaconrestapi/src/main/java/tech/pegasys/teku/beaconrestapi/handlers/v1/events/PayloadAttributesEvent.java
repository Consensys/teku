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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.ethereum.execution.types.Eth1Address.ETH1ADDRESS_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.PayloadAttributesEvent.PayloadAttributesData;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.json.types.SerializableArrayTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

public class PayloadAttributesEvent extends Event<PayloadAttributesData> {

  private static final SerializableTypeDefinition<PayloadAttributes> PAYLOAD_ATTRIBUTES_TYPE =
      SerializableTypeDefinition.object(PayloadAttributes.class)
          .name("PayloadAttributes")
          .withField("timestamp", UINT64_TYPE, PayloadAttributes::timestamp)
          .withField("prev_randao", BYTES32_TYPE, PayloadAttributes::prevRandao)
          .withField(
              "suggested_fee_recipient", ETH1ADDRESS_TYPE, PayloadAttributes::suggestedFeeRecipient)
          .withOptionalField(
              "withdrawals",
              new SerializableArrayTypeDefinition<>(Withdrawal.SSZ_SCHEMA.getJsonTypeDefinition()),
              PayloadAttributes::withdrawals)
          .withOptionalField(
              "parent_beacon_block_root",
              BYTES32_TYPE,
              payloadAttributes -> payloadAttributes.parentBeaconBlockRoot)
          .build();

  private static final SerializableTypeDefinition<PayloadAttributesEvent.Data> DATA_TYPE =
      SerializableTypeDefinition.object(PayloadAttributesEvent.Data.class)
          .name("PayloadAttributesEventData")
          .withField("proposal_slot", UINT64_TYPE, data -> data.proposalSlot)
          .withField("parent_block_root", BYTES32_TYPE, data -> data.parentBlockRoot)
          .withField(
              "parent_block_number",
              UINT64_TYPE,
              PayloadAttributesEvent.Data::parentExecutionBlockNumber)
          .withField(
              "parent_block_hash",
              BYTES32_TYPE,
              PayloadAttributesEvent.Data::parentExecutionBlockHash)
          .withField("proposer_index", UINT64_TYPE, data -> data.proposerIndex)
          .withField(
              "payload_attributes",
              PAYLOAD_ATTRIBUTES_TYPE,
              PayloadAttributesEvent.Data::payloadAttributes)
          .build();

  private static final SerializableTypeDefinition<PayloadAttributesData>
      PAYLOAD_ATTRIBUTES_EVENT_TYPE =
          SerializableTypeDefinition.object(PayloadAttributesData.class)
              .name("PayloadAttributesEvent")
              .withField("version", MILESTONE_TYPE, PayloadAttributesData::milestone)
              .withField("data", DATA_TYPE, PayloadAttributesData::data)
              .build();

  private PayloadAttributesEvent(final PayloadAttributesData data) {
    super(PAYLOAD_ATTRIBUTES_EVENT_TYPE, data);
  }

  record PayloadAttributesData(SpecMilestone milestone, Data data) {}

  record Data(
      UInt64 proposalSlot,
      Bytes32 parentBlockRoot,
      UInt64 parentExecutionBlockNumber,
      Bytes32 parentExecutionBlockHash,
      UInt64 proposerIndex,
      PayloadAttributes payloadAttributes) {}

  record PayloadAttributes(
      UInt64 timestamp,
      Bytes32 prevRandao,
      Eth1Address suggestedFeeRecipient,
      Optional<List<Withdrawal>> withdrawals,
      Optional<Bytes32> parentBeaconBlockRoot) {}

  /**
   * @param forkChoiceState The fork choice state before sending the fCu so can use it to get
   *     parent_block_number and parent_block_hash
   */
  static PayloadAttributesEvent create(
      final SpecMilestone milestone,
      final PayloadBuildingAttributes payloadAttributes,
      final ForkChoiceState forkChoiceState) {
    final PayloadAttributesData data =
        new PayloadAttributesData(
            milestone,
            new PayloadAttributesEvent.Data(
                payloadAttributes.getProposalSlot(),
                payloadAttributes.getParentBeaconBlockRoot(),
                forkChoiceState.getHeadExecutionBlockNumber(),
                forkChoiceState.getHeadExecutionBlockHash(),
                payloadAttributes.getProposerIndex(),
                // based on PayloadAttributesV<N> as defined by the execution-apis specification
                new PayloadAttributes(
                    payloadAttributes.getTimestamp(),
                    payloadAttributes.getPrevRandao(),
                    payloadAttributes.getFeeRecipient(),
                    payloadAttributes.getWithdrawals(),
                    milestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)
                        ? Optional.of(payloadAttributes.getParentBeaconBlockRoot())
                        : Optional.empty())));
    return new PayloadAttributesEvent(data);
  }
}

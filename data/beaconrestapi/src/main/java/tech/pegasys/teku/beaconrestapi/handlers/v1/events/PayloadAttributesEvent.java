/*
 * Copyright Consensys Software Inc., 2023
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

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH1ADDRESS_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.PayloadAttributesEvent.PayloadAttributesData;
import tech.pegasys.teku.infrastructure.json.types.SerializableArrayTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

public class PayloadAttributesEvent extends Event<PayloadAttributesData> {

  private static final SerializableTypeDefinition<PayloadBuildingAttributes>
      PAYLOAD_ATTRIBUTES_TYPE =
          SerializableTypeDefinition.object(PayloadBuildingAttributes.class)
              .name("PayloadAttributes")
              .withField("timestamp", UINT64_TYPE, PayloadBuildingAttributes::getTimestamp)
              .withField("prev_randao", BYTES32_TYPE, PayloadBuildingAttributes::getPrevRandao)
              .withField(
                  "suggested_fee_recipient",
                  ETH1ADDRESS_TYPE,
                  PayloadBuildingAttributes::getFeeRecipient)
              .withOptionalField(
                  "withdrawals",
                  new SerializableArrayTypeDefinition<>(
                      Withdrawal.SSZ_SCHEMA.getJsonTypeDefinition()),
                  PayloadBuildingAttributes::getWithdrawals)
              .withOptionalField(
                  "parent_beacon_block_root",
                  BYTES32_TYPE,
                  payloadAttributes ->
                      Optional.ofNullable(payloadAttributes.getParentBeaconBlockRoot()))
              .build();

  private static final SerializableTypeDefinition<PayloadAttributesEvent.Data> DATA_TYPE =
      SerializableTypeDefinition.object(PayloadAttributesEvent.Data.class)
          .name("PayloadAttributesEventData")
          .withField(
              "proposer_index", UINT64_TYPE, data -> data.payloadAttributes.getProposerIndex())
          .withField("proposal_slot", UINT64_TYPE, data -> data.payloadAttributes.getProposalSlot())
          .withField(
              "parent_block_number",
              UINT64_TYPE,
              PayloadAttributesEvent.Data::parentExecutionBlockNumber)
          .withField(
              "parent_block_root",
              BYTES32_TYPE,
              data -> data.payloadAttributes.getParentBeaconBlockRoot())
          .withField(
              "parent_block_hash",
              BYTES32_TYPE,
              PayloadAttributesEvent.Data::parentExecutionBlockHash)
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
      UInt64 parentExecutionBlockNumber,
      Bytes32 parentExecutionBlockHash,
      PayloadBuildingAttributes payloadAttributes) {}

  static PayloadAttributesEvent create(final PayloadAttributesData data) {
    return new PayloadAttributesEvent(data);
  }
}

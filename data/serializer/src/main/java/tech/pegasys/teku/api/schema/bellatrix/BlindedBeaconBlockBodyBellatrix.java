/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.api.schema.bellatrix;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.Deposit;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.api.schema.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.api.schema.altair.SyncAggregate;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;

public class BlindedBeaconBlockBodyBellatrix extends BeaconBlockBodyAltair {
  @JsonProperty("execution_payload_header")
  public final ExecutionPayloadHeader executionPayloadHeader;

  @JsonCreator
  public BlindedBeaconBlockBodyBellatrix(
      @JsonProperty("randao_reveal") final BLSSignature randaoReveal,
      @JsonProperty("eth1_data") final Eth1Data eth1Data,
      @JsonProperty("graffiti") final Bytes32 graffiti,
      @JsonProperty("proposer_slashings") final List<ProposerSlashing> proposerSlashings,
      @JsonProperty("attester_slashings") final List<AttesterSlashing> attesterSlashings,
      @JsonProperty("attestations") final List<Attestation> attestations,
      @JsonProperty("deposits") final List<Deposit> deposits,
      @JsonProperty("voluntary_exits") final List<SignedVoluntaryExit> voluntaryExits,
      @JsonProperty("sync_aggregate") final SyncAggregate syncAggregate,
      @JsonProperty("execution_payload_header")
          final ExecutionPayloadHeader executionPayloadHeader) {
    super(
        randaoReveal,
        eth1Data,
        graffiti,
        proposerSlashings,
        attesterSlashings,
        attestations,
        deposits,
        voluntaryExits,
        syncAggregate);
    checkNotNull(
        executionPayloadHeader,
        "Execution Payload Header is required for bellatrix blinded blocks");
    this.executionPayloadHeader = executionPayloadHeader;
  }

  @Override
  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody
      asInternalBeaconBlockBody(final SpecVersion spec) {

    ExecutionPayloadHeaderSchema executionPayloadHeaderSchema =
        spec.getSchemaDefinitions()
            .toVersionBellatrix()
            .orElseThrow()
            .getExecutionPayloadHeaderSchema();

    return super.asInternalBeaconBlockBody(
        spec,
        (builder) ->
            builder.executionPayloadHeader(
                () ->
                    executionPayloadHeaderSchema.create(
                        executionPayloadHeader.parentHash,
                        executionPayloadHeader.feeRecipient,
                        executionPayloadHeader.stateRoot,
                        executionPayloadHeader.receiptsRoot,
                        executionPayloadHeader.logsBloom,
                        executionPayloadHeader.prevRandao,
                        executionPayloadHeader.blockNumber,
                        executionPayloadHeader.gasLimit,
                        executionPayloadHeader.gasUsed,
                        executionPayloadHeader.timestamp,
                        executionPayloadHeader.extraData,
                        executionPayloadHeader.baseFeePerGas,
                        executionPayloadHeader.blockHash,
                        executionPayloadHeader.transactionsRoot)));
  }
}

/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.api.schema.altair;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlockBody;
import tech.pegasys.teku.api.schema.Deposit;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;

public class BeaconBlockBodyAltair extends BeaconBlockBody {
  public final SyncAggregate syncAggregate;

  public BeaconBlockBodyAltair(
      final BLSSignature randao_reveal,
      final Eth1Data eth1_data,
      final Bytes32 graffiti,
      final List<ProposerSlashing> proposer_slashings,
      final List<AttesterSlashing> attester_slashings,
      final List<Attestation> attestations,
      final List<Deposit> deposits,
      final List<SignedVoluntaryExit> voluntary_exits,
      final SyncAggregate sync_aggregate) {
    super(
        randao_reveal,
        eth1_data,
        graffiti,
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits);
    this.syncAggregate = sync_aggregate;
  }

  public BeaconBlockBodyAltair(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    super(body);
    if (!(body
        instanceof
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair
            .BeaconBlockBodyAltair)) {
      throw new IllegalArgumentException("Body passed was not an altair block body");
    }
    this.syncAggregate =
        ((tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair
                    .BeaconBlockBodyAltair)
                body)
            .getSyncAggregate();
  }
}

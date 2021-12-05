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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody;

import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;

public interface BeaconBlockBodyBuilder {

  BeaconBlockBodyBuilder randaoReveal(BLSSignature randaoReveal);

  BeaconBlockBodyBuilder eth1Data(Eth1Data eth1Data);

  BeaconBlockBodyBuilder graffiti(Bytes32 graffiti);

  BeaconBlockBodyBuilder attestations(SszList<Attestation> attestations);

  BeaconBlockBodyBuilder proposerSlashings(SszList<ProposerSlashing> proposerSlashings);

  BeaconBlockBodyBuilder attesterSlashings(SszList<AttesterSlashing> attesterSlashings);

  BeaconBlockBodyBuilder deposits(SszList<Deposit> deposits);

  BeaconBlockBodyBuilder voluntaryExits(SszList<SignedVoluntaryExit> voluntaryExits);

  // Not required by all hard forks so provided via a Supplier that is only invoked when needed.
  BeaconBlockBodyBuilder syncAggregate(Supplier<SyncAggregate> syncAggregateSupplier);

  // Not required by all hard forks so provided via a Supplier that is only invoked when needed.
  BeaconBlockBodyBuilder executionPayload(Supplier<ExecutionPayload> executionPayloadSupplier);

  BeaconBlockBody build();
}

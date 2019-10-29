/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.chain.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.ethereum.beacon.chain.observer.PendingOperations;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.operations.ProposerSlashing;
import org.ethereum.beacon.core.operations.VoluntaryExit;
import org.ethereum.beacon.core.operations.slashing.AttesterSlashing;
import org.mockito.Mockito;

public class PendingOperationsTestUtil {

  public static PendingOperations createEmptyPendingOperations() {
    return mockPendingOperations(
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList());
  }

  public static PendingOperations mockPendingOperations(
      List<Attestation> attestations,
      List<Attestation> aggregateAttestations,
      List<ProposerSlashing> proposerSlashings,
      List<AttesterSlashing> attesterSlashings,
      List<VoluntaryExit> voluntaryExits) {
    PendingOperations pendingOperations = Mockito.mock(PendingOperations.class);
    when(pendingOperations.getAttestations()).thenReturn(attestations);
    when(pendingOperations.peekProposerSlashings(anyInt())).thenReturn(proposerSlashings);
    when(pendingOperations.peekAttesterSlashings(anyInt())).thenReturn(attesterSlashings);
    when(pendingOperations.peekAggregateAttestations(anyInt(), any()))
        .thenReturn(aggregateAttestations);
    when(pendingOperations.peekExits(anyInt())).thenReturn(voluntaryExits);
    return pendingOperations;
  }
}

/*
 * Copyright 2020 ConsenSys AG.
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyFactory;

@SuppressWarnings("FutureReturnValueIgnored")
public abstract class AbstractDutySchedulerTest {
  static final BLSPublicKey VALIDATOR1_KEY = BLSPublicKey.random(100);
  static final BLSPublicKey VALIDATOR2_KEY = BLSPublicKey.random(200);
  static final Collection<BLSPublicKey> VALIDATOR_KEYS = Set.of(VALIDATOR1_KEY, VALIDATOR2_KEY);
  static final List<Integer> VALIDATOR_INDICES = List.of(123, 559);
  final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  final Signer validator1Signer = mock(Signer.class);
  final Signer validator2Signer = mock(Signer.class);
  final Validator validator1 = new Validator(VALIDATOR1_KEY, validator1Signer, Optional::empty);
  final Validator validator2 = new Validator(VALIDATOR2_KEY, validator2Signer, Optional::empty);

  final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  final ValidatorDutyFactory dutyFactory = mock(ValidatorDutyFactory.class);
  final ForkProvider forkProvider = mock(ForkProvider.class);
  final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  final ForkInfo fork = dataStructureUtil.randomForkInfo();
  final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  @BeforeEach
  public void setUp() {
    when(validatorIndexProvider.getValidatorIndices(VALIDATOR_KEYS))
        .thenReturn(SafeFuture.completedFuture(VALIDATOR_INDICES));
    when(dutyFactory.createAttestationProductionDuty(any()))
        .thenReturn(mock(AttestationProductionDuty.class));
    final SafeFuture<BLSSignature> rejectAggregationSignature =
        SafeFuture.failedFuture(new UnsupportedOperationException("This test ignores aggregation"));
    when(validator1Signer.signAggregationSlot(any(), any())).thenReturn(rejectAggregationSignature);
    when(validator2Signer.signAggregationSlot(any(), any())).thenReturn(rejectAggregationSignature);
    when(forkProvider.getForkInfo()).thenReturn(completedFuture(fork));
  }
}

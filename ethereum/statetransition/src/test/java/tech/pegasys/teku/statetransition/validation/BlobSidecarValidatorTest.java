/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.statetransition.validation;

import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;

@TestSpecContext(milestone = {SpecMilestone.DENEB})
public class BlobSidecarValidatorTest {
  private final Map<Bytes32, BlockImportResult> invalidBlocks = new HashMap<>();
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private BlobSidecarValidator blobSidecarValidator;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    blobSidecarValidator =
        BlobSidecarValidator.create(specContext.getSpec(), invalidBlocks, gossipValidationHelper);
  }
}

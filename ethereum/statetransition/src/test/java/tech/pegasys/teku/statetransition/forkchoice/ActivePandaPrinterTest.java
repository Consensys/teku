/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.storage.store.MemKeyValueStore;

class ActivePandaPrinterTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final KeyValueStore<String, Bytes> keyValueStore = new MemKeyValueStore<>();

  private final StatusLogger logger = mock(StatusLogger.class);

  private final PandaPrinter pandaPrinter = new ActivePandaPrinter(keyValueStore, logger);
  private SignedBlockAndState preMerge;
  private SignedBlockAndState merge;
  private SignedBlockAndState postMerge;

  @BeforeEach
  void setUp() {
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    chainBuilder.generateGenesis();
    preMerge = chainBuilder.generateBlockAtSlot(1);
    merge =
        chainBuilder.generateBlockAtSlot(
            2,
            BlockOptions.create()
                .setExecutionPayload(dataStructureUtil.randomExecutionPayload())
                .setSkipStateTransition(true));
    postMerge =
        chainBuilder.generateBlockAtSlot(3, BlockOptions.create().setSkipStateTransition(true));
  }

  @Test
  void shouldPrintPandasWhenFirstMergeBlockImported() {
    pandaPrinter.onBlockImported(preMerge.getState(), merge.getBlock());
    verify(logger).posActivated(anyString());
  }

  @Test
  void shouldNotPrintPandasForBlocksAfterMergeCompleted() {
    pandaPrinter.onBlockImported(merge.getState(), postMerge.getBlock());
    verifyNoInteractions(logger);
  }

  @Test
  void shouldNotPrintPandasBeforeMergeCompletes() {
    pandaPrinter.onBlockImported(preMerge.getState(), preMerge.getBlock());
    verifyNoInteractions(logger);
  }

  @Test
  void shouldNotPrintPandasWhenFirstMergeBlockImportedIfAlreadyPrinted() {
    pandaPrinter.onBlockImported(preMerge.getState(), merge.getBlock());
    verify(logger).posActivated(anyString());

    pandaPrinter.onBlockImported(preMerge.getState(), merge.getBlock());
    verifyNoMoreInteractions(logger);
  }

  @Test
  void shouldNotPrintPandasWhenPrintedInPreviousInstance() {
    pandaPrinter.onBlockImported(preMerge.getState(), merge.getBlock());
    verify(logger).posActivated(anyString());

    PandaPrinter secondPandaPrinter = new ActivePandaPrinter(keyValueStore, logger);
    secondPandaPrinter.onBlockImported(preMerge.getState(), merge.getBlock());
    verifyNoMoreInteractions(logger);
  }
}

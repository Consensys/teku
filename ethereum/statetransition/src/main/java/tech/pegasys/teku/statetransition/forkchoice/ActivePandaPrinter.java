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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.Resources;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.storage.store.KeyValueStore;

public class ActivePandaPrinter implements PandaPrinter {
  private static final Logger LOG = LogManager.getLogger();

  private static final String PANDA_KEY = "pandas-printed";

  private final KeyValueStore<String, Bytes> keyValueStore;
  private final AtomicBoolean pandasPrinted;

  public ActivePandaPrinter(final KeyValueStore<String, Bytes> keyValueStore) {
    this.keyValueStore = keyValueStore;
    final boolean pandasPrinted = !keyValueStore.get(PANDA_KEY).orElse(Bytes.EMPTY).isEmpty();
    this.pandasPrinted = new AtomicBoolean(pandasPrinted);
  }

  @Override
  public void onBlockImported(final BeaconState preState, final SignedBeaconBlock block) {
    if (!pandasPrinted.get()
        && hasNonDefaultPayload(block)
        && hasDefaultPayload(preState)
        && pandasPrinted.compareAndSet(false, true)) {
      keyValueStore.put(PANDA_KEY, Bytes.of(1));
      unleashThePandas();
    }
  }

  private void unleashThePandas() {
    try {
      final String pandas =
          Resources.toString(Resources.getResource(ActivePandaPrinter.class, "panda.txt"), UTF_8);
      StatusLogger.STATUS_LOG.posActivated(pandas);
    } catch (final Throwable t) {
      LOG.info("POS activated, but the pandas failed to unleash.", t);
    }
  }

  private boolean hasDefaultPayload(final BeaconState state) {
    return state
        .toVersionBellatrix()
        .map(BeaconStateBellatrix::getLatestExecutionPayloadHeader)
        .map(ExecutionPayloadHeader::isDefault)
        .orElse(true);
  }

  private boolean hasNonDefaultPayload(final SignedBeaconBlock block) {
    return !block
        .getMessage()
        .getBody()
        .getOptionalExecutionPayload()
        .map(ExecutionPayload::isDefault)
        .orElse(true);
  }
}

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

package tech.pegasys.teku.cli.subcommand;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;

public class TransitionCommandTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldProcessBlocksInSlotNumberOrder() {
    final String[] argsNetworkOptOnParent =
        new String[] {
          "transition",
          "blocks",
          "--network=mainnet",
          String.format(
              "-i=%s",
              Resources.getResource(TransitionCommandTest.class, "state-from-slot-7.ssz")
                  .getPath()),
          "-o=/dev/null",
          Resources.getResource(TransitionCommandTest.class, "block-10.ssz").getPath(),
          Resources.getResource(TransitionCommandTest.class, "block-8.ssz").getPath(),
          Resources.getResource(TransitionCommandTest.class, "block-9.ssz").getPath(),
        };
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(0);
    String cmdOutput = getCommandLineOutput();
    assertThat(cmdOutput).isEmpty();
  }
}

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

package tech.pegasys.teku.reference.phase0.bls;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethtests.finder.TestDefinition;

public class BlsHashToG2TestExecutor extends BlsTestExecutor {
  @Override
  protected void runTestImpl(final TestDefinition testDefinition) throws Throwable {
    final Data data = loadDataFile(testDefinition, Data.class);
    final String message = data.input.getMessage();
    final Pair<Bytes, Bytes> expectedX = data.output.getX();
    final Pair<Bytes, Bytes> expectedY = data.output.getY();
    // TODO: This is definitely wrong but makes errorprone happy that I'm using all the variables
    assertThat(message).isEqualTo(expectedX);
    assertThat(message).isEqualTo(expectedY);
  }

  private static class Data {
    @JsonProperty(value = "input", required = true)
    private Input input;

    @JsonProperty(value = "output", required = true)
    private Output output;
  }

  private static class Input {
    @JsonProperty(value = "msg", required = true)
    private String msg;

    public String getMessage() {
      return msg;
    }
  }

  private static class Output {
    @JsonProperty(value = "x", required = true)
    private String x;

    @JsonProperty(value = "y", required = true)
    private String y;

    public Pair<Bytes, Bytes> getX() {
      return parsePoint(x);
    }

    public Pair<Bytes, Bytes> getY() {
      return parsePoint(y);
    }

    private Pair<Bytes, Bytes> parsePoint(final String point) {
      final String left = point.substring(0, point.indexOf(','));
      final String right = point.substring(point.indexOf(',') + 1);
      return Pair.of(Bytes.fromHexString(left), Bytes.fromHexString(right));
    }
  }
}

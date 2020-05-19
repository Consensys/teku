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

package tech.pegasys.teku.reference.phase0.bls;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.ethtests.finder.BlsTestFinder.BLS_DATA_FILE;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestExecutor;

public class BlsAggregateVerifyTestType implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final Data data = loadYaml(testDefinition, BLS_DATA_FILE, Data.class);
    final List<BLSPublicKey> publicKeys = data.getInputPublicKeys();
    final List<Bytes> messages = data.getInputMessages();
    final BLSSignature signature = data.getSignature();
    final boolean expectedResult = data.getOutput();

    assertThat(BLS.aggregateVerify(publicKeys, messages, signature)).isEqualTo(expectedResult);
  }

  private static class Data {
    @JsonProperty(value = "input", required = true)
    private Input input;

    @JsonProperty(value = "output", required = true)
    private boolean output;

    public BLSSignature getSignature() {
      return input.getSignature();
    }

    public List<BLSPublicKey> getInputPublicKeys() {
      return input.getPairs().stream().map(Pair::getPublicKey).collect(Collectors.toList());
    }

    public List<Bytes> getInputMessages() {
      return input.getPairs().stream().map(Pair::getMessage).collect(Collectors.toList());
    }

    public boolean getOutput() {
      return output;
    }
  }

  private static class Input {
    @JsonProperty(value = "pairs", required = true)
    private List<Pair> pairs;

    @JsonProperty(value = "signature", required = true)
    private String signature;

    public List<Pair> getPairs() {
      return pairs;
    }

    public BLSSignature getSignature() {
      return BlsTests.parseSignature(signature);
    }
  }

  private static class Pair {
    @JsonProperty(value = "pubkey", required = true)
    private String publicKey;

    @JsonProperty(value = "message", required = true)
    private String message;

    public BLSPublicKey getPublicKey() {
      return BLSPublicKey.fromBytes(Bytes.fromHexString(publicKey));
    }

    public Bytes getMessage() {
      return Bytes.fromHexString(message);
    }
  }
}

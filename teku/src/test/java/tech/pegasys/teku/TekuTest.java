/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TekuTest {

  private static final String OPTION = "--Xnetty-max-direct-memory";

  @Test
  void extractCliOptionValue_returnsValueFromEqualsForm() {
    final String[] args = {"--p2p-port=9000", OPTION + "=128", "--metrics-enabled"};
    assertThat(Teku.extractCliOptionValue(args, OPTION)).isEqualTo("128");
  }

  @Test
  void extractCliOptionValue_returnsValueFromSpaceSeparatedForm() {
    final String[] args = {"--p2p-port", "9000", OPTION, "64", "--metrics-enabled"};
    assertThat(Teku.extractCliOptionValue(args, OPTION)).isEqualTo("64");
  }

  @Test
  void extractCliOptionValue_returnsNullWhenAbsent() {
    final String[] args = {"--p2p-port=9000", "--metrics-enabled"};
    assertThat(Teku.extractCliOptionValue(args, OPTION)).isNull();
  }

  @Test
  void extractCliOptionValue_returnsNullWhenSpaceFormHasNoFollowingValue() {
    final String[] args = {"--p2p-port=9000", OPTION};
    assertThat(Teku.extractCliOptionValue(args, OPTION)).isNull();
  }

  @Test
  void extractCliOptionValue_doesNotMatchPrefixOfOtherOption() {
    final String[] args = {OPTION + "-extra=99", "--metrics-enabled"};
    assertThat(Teku.extractCliOptionValue(args, OPTION)).isNull();
  }

  @Test
  void megabytesToBytes_convertsIntegerMb() {
    assertThat(Teku.megabytesToBytes("128")).isEqualTo(128L * 1024 * 1024);
    assertThat(Teku.megabytesToBytes("1")).isEqualTo(1024L * 1024);
    assertThat(Teku.megabytesToBytes(" 64 ")).isEqualTo(64L * 1024 * 1024);
  }

  @Test
  void megabytesToBytes_rejectsNonIntegerInput() {
    assertThatThrownBy(() -> Teku.megabytesToBytes("128MB"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Teku.megabytesToBytes(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Teku.megabytesToBytes("128.5"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void megabytesToBytes_rejectsZeroAndNegative() {
    assertThatThrownBy(() -> Teku.megabytesToBytes("0"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Teku.megabytesToBytes("-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

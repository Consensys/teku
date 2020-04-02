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

package tech.pegasys.artemis.beaconrestapi.beacon;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.api.schema.Committee;
import tech.pegasys.artemis.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.artemis.beaconrestapi.RestApiConstants;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetCommittees;

public class GetCommitteesWithDataIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  public static final UnsignedLong TWENTY = UnsignedLong.valueOf(20L);
  public static final UnsignedLong SIXTEEN = UnsignedLong.valueOf(16L);
  public static final UnsignedLong TWENTY_FOUR = UnsignedLong.valueOf(24L);

  @Test
  void shouldGetCommitteesAtCurrentEpochWhenBlockIsMissing() throws Exception {
    withBlockDataAtSlot(NINE, TEN, TWENTY);

    List<Committee> result = getCommitteesByEpoch(2);
    assertThat(result.get(0).slot).isEqualTo(SIXTEEN);
  }

  @Test
  void shouldGetCommitteesAtNextEpochWhenBlockIsMissing() throws Exception {
    withBlockDataAtSlot(NINE, TEN, TWENTY);

    List<Committee> result = getCommitteesByEpoch(3);
    assertThat(result.get(0).slot).isEqualTo(TWENTY_FOUR);
  }

  @Test
  void shouldGetCommitteesAtFutureEpochWhenBlockIsMissing() throws Exception {
    withBlockDataAtSlot(NINE, TEN, TWENTY);

    // currently at epoch 2, epoch 3 will be available, but 4 will be missing,
    // as committees are only calculated for 1 future epoch
    final Response response = getByEpoch(4);
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void shouldGetCommitteesAtCurrentEpochWithBlockPresent() throws Exception {
    withBlockDataAtSlot(SEVEN, EIGHT, NINE, TEN);

    List<Committee> result = getCommitteesByEpoch(1);
    assertThat(result.get(0).slot).isEqualTo(EIGHT);
  }

  @Test
  void shouldGetCommitteesAtNextEpochWithBlockPresent() throws Exception {
    withBlockDataAtSlot(SEVEN, EIGHT, NINE, TEN);

    List<Committee> result = getCommitteesByEpoch(2);
    assertThat(result.get(0).slot).isEqualTo(SIXTEEN);
  }

  private List<Committee> getCommitteesByEpoch(final int epoch) throws IOException {
    final Response response = getByEpoch(epoch);
    final String responseBody = response.body().string();
    assertThat(response.code()).isEqualTo(SC_OK);
    final Committee[] result = jsonProvider.jsonToObject(responseBody, Committee[].class);
    return List.of(result);
  }

  private Response getByEpoch(final int epoch) throws IOException {
    return getResponse(
        GetCommittees.ROUTE, Map.of(RestApiConstants.EPOCH, Integer.toString(epoch, 10)));
  }
}

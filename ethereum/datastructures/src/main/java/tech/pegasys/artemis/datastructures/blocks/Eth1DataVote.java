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

package tech.pegasys.artemis.datastructures.blocks;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Copyable;

public final class Eth1DataVote implements Copyable<Eth1DataVote> {

  private Eth1Data eth1_data;
  private long vote_count;

  public Eth1DataVote(Eth1Data eth1_data, long vote_count) {
    this.eth1_data = eth1_data;
    this.vote_count = vote_count;
  }

  public Eth1DataVote(Eth1DataVote eth1DataVote) {
    this.eth1_data = new Eth1Data(eth1DataVote.getEth1_data());
    this.vote_count = eth1DataVote.getVote_count();
  }

  @Override
  public Eth1DataVote copy() {
    return new Eth1DataVote(this);
  }

  public static Eth1DataVote fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader -> new Eth1DataVote(Eth1Data.fromBytes(reader.readBytes()), reader.readUInt64()));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(eth1_data.toBytes());
          writer.writeUInt64(vote_count);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(eth1_data, vote_count);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Eth1DataVote)) {
      return false;
    }

    Eth1DataVote other = (Eth1DataVote) obj;
    return Objects.equals(this.getEth1_data(), other.getEth1_data())
        && Objects.equals(this.getVote_count(), other.getVote_count());
  }

  /** @return the eth1_data */
  public Eth1Data getEth1_data() {
    return eth1_data;
  }

  /** @param eth1_data the eth1_data to set */
  public void setEth1_data(Eth1Data eth1_data) {
    this.eth1_data = eth1_data;
  }

  /** @return the vote_count */
  public long getVote_count() {
    return vote_count;
  }

  /** @param vote_count the vote_count to set */
  public void setVote_count(long vote_count) {
    this.vote_count = vote_count;
  }
}

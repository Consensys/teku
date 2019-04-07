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

package tech.pegasys.artemis.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes32;

public class TimeSeriesRecord implements IRecordAdapter {

  private Long index;
  private Long slot;
  private Long epoch;
  private String headBlockRoot;
  private String headStateRoot;
  private String parentHeadBlockRoot;
  private Long numValidators;
  private String justifiedBlockRoot;
  private String justifiedStateRoot;
  private String finalizedBlockRoot;
  private String finalizedStateRoot;

  public TimeSeriesRecord() {
    // new Hello(1, 1, Bytes32.random(), UInt64.valueOf(0), Bytes32.random(), UInt64.valueOf(0))
    this.index = Long.MAX_VALUE;
    this.slot = Long.MAX_VALUE;
    this.epoch = Long.MAX_VALUE;
    this.headBlockRoot = Bytes32.ZERO.toHexString();
    this.headStateRoot = Bytes32.ZERO.toHexString();
    this.parentHeadBlockRoot = Bytes32.ZERO.toHexString();
    this.numValidators = Long.MAX_VALUE;
    this.justifiedBlockRoot = Bytes32.ZERO.toHexString();
    this.justifiedStateRoot = Bytes32.ZERO.toHexString();
    this.finalizedBlockRoot = Bytes32.ZERO.toHexString();
    this.finalizedStateRoot = Bytes32.ZERO.toHexString();
  }

  public TimeSeriesRecord(
      Long index,
      Long slot,
      Long epoch,
      String headBlockRoot,
      String headStateRoot,
      String parentHeadBlockRoot,
      Long numValidators,
      String justifiedBlockRoot,
      String justifiedStateRoot,
      String finalizedBlockRoot,
      String finalizedStateRoot) {
    this.index = index;
    this.slot = slot;
    this.epoch = epoch;
    this.headBlockRoot = headBlockRoot;
    this.headStateRoot = headStateRoot;
    this.parentHeadBlockRoot = parentHeadBlockRoot;
    this.numValidators = numValidators;
    this.justifiedBlockRoot = justifiedBlockRoot;
    this.justifiedStateRoot = justifiedStateRoot;
    this.finalizedBlockRoot = finalizedBlockRoot;
    this.finalizedStateRoot = finalizedStateRoot;
  }

  public Long getIndex() {
    return this.index;
  }

  public void setIndex(Long index) {
    this.index = index;
  }

  public Long getSlot() {
    return this.slot;
  }

  public void setSlot(Long slot) {
    this.slot = slot;
  }

  public Long getEpoch() {
    return this.epoch;
  }

  public void setEpoch(Long epoch) {
    this.epoch = epoch;
  }

  public String getHeadBlockRoot() {
    return this.headBlockRoot;
  }

  public void setHeadBlockRoot(String headBlockRoot) {
    this.headBlockRoot = headBlockRoot;
  }

  public String getHeadStateRoot() {
    return this.headStateRoot;
  }

  public void setHeadStateRoot(String headStateRoot) {
    this.headStateRoot = headStateRoot;
  }

  public String getParentHeadBlockRoot() {
    return this.parentHeadBlockRoot;
  }

  public void setParentHeadBlockRoot(String parentHeadBlockRoot) {
    this.parentHeadBlockRoot = parentHeadBlockRoot;
  }

  public Long getNumValidators() {
    return this.numValidators;
  }

  public void setNumValidators(Long numValidators) {
    this.numValidators = numValidators;
  }

  public String getJustifiedBlockRoot() {
    return this.justifiedBlockRoot;
  }

  public void setJustifiedBlockRoot(String justifiedBlockRoot) {
    this.justifiedBlockRoot = justifiedBlockRoot;
  }

  public String getJustifiedStateRoot() {
    return this.justifiedStateRoot;
  }

  public void setJustifiedStateRoot(String justifiedStateRoot) {
    this.justifiedStateRoot = justifiedStateRoot;
  }

  public String getFinalizedBlockRoot() {
    return this.finalizedBlockRoot;
  }

  public void setFinalizedBlockRoot(String finalizedBlockRoot) {
    this.finalizedBlockRoot = finalizedBlockRoot;
  }

  public String getFinalizedStateRoot() {
    return this.finalizedStateRoot;
  }

  public void setFinalizedStateRoot(String finalizedStateRoot) {
    this.finalizedStateRoot = finalizedStateRoot;
  }

  public TimeSeriesRecord index(Long index) {
    this.index = index;
    return this;
  }

  public TimeSeriesRecord slot(Long slot) {
    this.slot = slot;
    return this;
  }

  public TimeSeriesRecord epoch(Long epoch) {
    this.epoch = epoch;
    return this;
  }

  public TimeSeriesRecord headBlockRoot(String headBlockRoot) {
    this.headBlockRoot = headBlockRoot;
    return this;
  }

  public TimeSeriesRecord headStateRoot(String headStateRoot) {
    this.headStateRoot = headStateRoot;
    return this;
  }

  public TimeSeriesRecord parentHeadBlockRoot(String parentHeadBlockRoot) {
    this.parentHeadBlockRoot = parentHeadBlockRoot;
    return this;
  }

  public TimeSeriesRecord numValidators(Long numValidators) {
    this.numValidators = numValidators;
    return this;
  }

  public TimeSeriesRecord justifiedBlockRoot(String justifiedBlockRoot) {
    this.justifiedBlockRoot = justifiedBlockRoot;
    return this;
  }

  public TimeSeriesRecord justifiedStateRoot(String justifiedStateRoot) {
    this.justifiedStateRoot = justifiedStateRoot;
    return this;
  }

  public TimeSeriesRecord finalizedBlockRoot(String finalizedBlockRoot) {
    this.finalizedBlockRoot = finalizedBlockRoot;
    return this;
  }

  public TimeSeriesRecord finalizedStateRoot(String finalizedStateRoot) {
    this.finalizedStateRoot = finalizedStateRoot;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof TimeSeriesRecord)) {
      return false;
    }
    TimeSeriesRecord timeSeriesRecord = (TimeSeriesRecord) o;
    return Objects.equals(index, timeSeriesRecord.index)
        && Objects.equals(slot, timeSeriesRecord.slot)
        && Objects.equals(epoch, timeSeriesRecord.epoch)
        && Objects.equals(headBlockRoot, timeSeriesRecord.headBlockRoot)
        && Objects.equals(headStateRoot, timeSeriesRecord.headStateRoot)
        && Objects.equals(parentHeadBlockRoot, timeSeriesRecord.parentHeadBlockRoot)
        && Objects.equals(numValidators, timeSeriesRecord.numValidators)
        && Objects.equals(justifiedBlockRoot, timeSeriesRecord.justifiedBlockRoot)
        && Objects.equals(justifiedStateRoot, timeSeriesRecord.justifiedStateRoot)
        && Objects.equals(finalizedBlockRoot, timeSeriesRecord.finalizedBlockRoot)
        && Objects.equals(finalizedStateRoot, timeSeriesRecord.finalizedStateRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        index,
        slot,
        epoch,
        headBlockRoot,
        headStateRoot,
        parentHeadBlockRoot,
        numValidators,
        justifiedBlockRoot,
        justifiedStateRoot,
        finalizedBlockRoot,
        finalizedStateRoot);
  }

  @Override
  public String toJSON() {
    Gson gson = new GsonBuilder().create();
    return gson.toJson(this);
  }

  @Override
  public String toCSV() {
    return " '"
        + this.getIndex()
        + "'"
        + ", '"
        + this.getSlot()
        + "'"
        + ", '"
        + this.getEpoch()
        + "'"
        + ", '"
        + this.getHeadBlockRoot()
        + "'"
        + ", '"
        + this.getHeadStateRoot()
        + "'"
        + ", '"
        + this.getParentHeadBlockRoot()
        + "'"
        + ", '"
        + this.getNumValidators()
        + "'"
        + ", '"
        + this.getJustifiedBlockRoot()
        + "'"
        + ", '"
        + this.getJustifiedStateRoot()
        + "'";
  }
}

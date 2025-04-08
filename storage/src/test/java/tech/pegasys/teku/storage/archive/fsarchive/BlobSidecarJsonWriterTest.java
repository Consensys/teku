/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.storage.archive.fsarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlobSidecarJsonWriterTest {
  private static final Spec SPEC = TestSpecFactory.createMinimalDeneb();

  BlobSidecarJsonWriter blobSidecarJsonWriter;
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  public void test() {
    this.blobSidecarJsonWriter = new BlobSidecarJsonWriter();
    this.dataStructureUtil = new DataStructureUtil(SPEC);
  }

  @Test
  void testWriteSlotBlobSidecarsWithEmptyList() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    List<BlobSidecar> blobSidecars = new ArrayList<>();
    blobSidecarJsonWriter.writeSlotBlobSidecars(out, blobSidecars);
    String json = out.toString(StandardCharsets.UTF_8);
    assertEquals("[]", json);
  }

  @Test
  void testWriteSlotBlobSidecarsWithSingleElement() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    List<BlobSidecar> blobSidecars = new ArrayList<>();
    final BlobSidecar blobSidecar =
        dataStructureUtil.randomBlobSidecarForBlock(
            dataStructureUtil.randomSignedBeaconBlock(1), 0);
    blobSidecars.add(blobSidecar);
    blobSidecarJsonWriter.writeSlotBlobSidecars(out, blobSidecars);
    String json = out.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("index"));
    assertTrue(json.contains("blob"));
    assertTrue(json.contains("kzg_commitment"));
    assertTrue(json.contains("kzg_proof"));
    assertTrue(json.contains("signed_block_header"));
    assertTrue(json.contains("parent_root"));
    assertTrue(json.contains("state_root"));
    assertTrue(json.contains("body_root"));
    assertTrue(json.contains("signature"));
  }

  @Test
  void testWriteSlotBlobSidecarsNulls() {
    assertThrows(
        NullPointerException.class, () -> blobSidecarJsonWriter.writeSlotBlobSidecars(null, null));
  }

  @Test
  void testWriteSlotBlobSidecarsNullOut() {
    assertThrows(
        NullPointerException.class,
        () -> {
          List<BlobSidecar> blobSidecars = new ArrayList<>();
          blobSidecarJsonWriter.writeSlotBlobSidecars(null, blobSidecars);
        });
  }

  @Test
  void testWriteSlotBlobSidecarsNullList() {
    assertThrows(
        NullPointerException.class,
        () -> {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          blobSidecarJsonWriter.writeSlotBlobSidecars(out, null);
        });
  }
}

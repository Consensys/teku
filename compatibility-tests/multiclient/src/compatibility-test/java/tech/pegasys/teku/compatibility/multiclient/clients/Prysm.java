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

package tech.pegasys.teku.compatibility.multiclient.clients;

import static java.time.temporal.ChronoUnit.MINUTES;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class Prysm extends GenericContainer<Prysm> implements BeaconChainNode {

  private static final Logger LOG = LogManager.getLogger();
  private static final String PRIVKEY_TARGET_PATH = "/tmp/privkey.tmp";
  private final File privKeyFile;
  private final NodeId nodeId;

  public Prysm() {
    super("gcr.io/prysmaticlabs/prysm/beacon-chain:latest");
    try {
      PrivKey privKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
      PubKey pubKey = privKey.publicKey();
      nodeId = new LibP2PNodeId(PeerId.fromPubKey(pubKey));

      privKeyFile = writePrivKeyToFile(privKey);

      withExposedPorts(13000);
      withCommand(
          "--p2p-tcp-port",
          "13000",
          "--no-discovery",
          "--minimal-config",
          "--p2p-priv-key",
          PRIVKEY_TARGET_PATH,
          "--verbosity",
          "INFO");
      withStartupTimeout(Duration.of(2, MINUTES));
      waitingFor(Wait.forLogMessage(".*Node started p2p server.*", 1));
      withCopyFileToContainer(
          MountableFile.forHostPath(privKeyFile.getAbsolutePath()), PRIVKEY_TARGET_PATH);
      withLogConsumer(outputFrame -> LOG.debug(outputFrame.getUtf8String().trim()));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private File writePrivKeyToFile(final PrivKey privKey) throws IOException {
    final File privKeyFile = File.createTempFile("prysm-priv-key", ".key");
    privKeyFile.deleteOnExit();

    // Prysm is particularly picky about how the private key is written.
    // It does not support the 33 byte version where a leading 0 is added to ensure the
    // number is always interpreted as positive.
    Bytes rawBytes = Bytes.wrap(privKey.raw());
    if (rawBytes.size() == 33) {
      rawBytes = rawBytes.slice(1, 32);
    }
    // And it doesn't accept a 0x prefix so we can't just use toHexString().
    Files.writeString(privKeyFile.toPath(), rawBytes.appendHexTo(new StringBuilder()));
    return privKeyFile;
  }

  @Override
  public String getMultiAddr() {
    return "/ip4/127.0.0.1/tcp/" + getMappedPort(13000) + "/p2p/" + nodeId.toBase58();
  }

  @Override
  public NodeId getId() {
    return nodeId;
  }

  @Override
  public void stop() {
    super.stop();
    try {
      Files.deleteIfExists(privKeyFile.toPath());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}

package tech.pegasys.artemis.benchmarks;

import static org.mockito.Mockito.mock;

import com.google.common.eventbus.EventBus;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.benchmarks.gen.BlockIO;
import tech.pegasys.artemis.benchmarks.gen.BlockIO.Reader;
import tech.pegasys.artemis.benchmarks.gen.BlsKeyPairIO;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.collections.LimitedHashMap;
import tech.pegasys.artemis.util.config.Constants;

public class ProfilingRun {


  @Test
  public void a() {
    LimitedHashMap<String, String> map = new LimitedHashMap<>(0);
    map.computeIfAbsent("a", k -> {
      System.out.println("Computed");
      return "c";
    });
    map.computeIfAbsent("a", k -> {
      System.out.println("Computed");
      return "c";
    });
  }

  @Disabled
  @Test
  public void importBlocks() throws Exception {

    Constants.SLOTS_PER_EPOCH = 6;
    BeaconStateUtil.BLS_VERIFY_DEPOSIT = false;
    BeaconStateUtil.DEPOSIT_PROOFS_ENABLED = false;

    int validatorsCount = 3 * 1024;

    String blocksFile =
        "/blocks/blocks_epoch_"
            + Constants.SLOTS_PER_EPOCH
            + "_validators_"
            + validatorsCount
            + ".ssz.gz";

    System.out.println("Start blocks import from " + blocksFile);
    try (Reader blockReader = BlockIO.createResourceReader(blocksFile)) {
      for (BeaconBlock block : blockReader) {
        System.out.println(block);
      }
    }
    System.out.println("Generating keypairs...");
    //    List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(validatorsCount);
    //    List<BLSKeyPair> validatorKeys =
    // BlsKeyPairIO.createReaderWithDefaultSource().readAll(validatorsCount);
    List<BLSKeyPair> validatorKeys =
        BlsKeyPairIO.createReaderForResource("/bls-key-pairs/bls-key-pairs-100k-seed-0.txt.gz")
            .readAll(validatorsCount);

    EventBus localEventBus = mock(EventBus.class);
    ChainStorageClient localStorage = ChainStorageClient.memoryOnlyClient(localEventBus);
    BeaconChainUtil localChain = BeaconChainUtil.create(localStorage, validatorKeys, false);
    localChain.initializeStorage();

    BlockImporter blockImporter = new BlockImporter(localStorage, localEventBus);

    System.out.println("Start blocks import from " + blocksFile);
    try (Reader blockReader = BlockIO.createResourceReader(blocksFile)) {
      for (BeaconBlock block : blockReader) {
        long s = System.currentTimeMillis();
        localChain.setSlot(block.getSlot());
        BlockImportResult result = blockImporter.importBlock(block);
        System.out.println(
            "Imported block at #"
                + block.getSlot()
                + " in "
                + (System.currentTimeMillis() - s)
                + " ms: "
                + result);
      }
    }
  }

}

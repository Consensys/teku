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

package org.ethereum.beacon.pow;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.codec.binary.Hex;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.ChainStart;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.MutableBeaconState;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.core.spec.SignatureDomains;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.crypto.BLS381;
import org.ethereum.beacon.crypto.Hashes;
import org.ethereum.beacon.crypto.MessageParameters;
import org.ethereum.beacon.crypto.util.BlsKeyPairGenerator;
import org.ethereum.beacon.pow.DepositContract.DepositInfo;
import org.ethereum.beacon.schedulers.Schedulers;
import org.ethereum.config.SystemProperties;
import org.ethereum.facade.Ethereum;
import org.ethereum.solidity.compiler.CompilationResult.ContractMetadata;
import org.ethereum.util.blockchain.SolidityCallResult;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.util.blockchain.StandaloneBlockchain;
import org.ethereum.util.blockchain.StandaloneBlockchain.SolidityContractImpl;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import reactor.core.publisher.Mono;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes8;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.bytes.MutableBytes48;
import tech.pegasys.artemis.util.uint.UInt64;

@Ignore
public class StandaloneDepositContractTest {

  // modified spec constants:
  // GENESIS_ACTIVE_VALIDATOR_COUNT = 16  # 2**14
  // SECONDS_PER_DAY = 5

  final int MERKLE_TREE_DEPTH =
      BeaconChainSpec.DEFAULT_CONSTANTS.getDepositContractTreeDepth().intValue();
  final Function<BytesValue, Hash32> HASH_FUNCTION = Hashes::sha256;
  String depositBin =
      "740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a052341561009857600080fd5b6101406000601f818352015b600061014051602081106100b757600080fd5b600260c052602060c020015460208261016001015260208101905061014051602081106100e357600080fd5b600260c052602060c020015460208261016001015260208101905080610160526101609050602060c0825160208401600060025af161012157600080fd5b60c0519050606051600161014051018060405190131561014057600080fd5b809190121561014e57600080fd5b6020811061015b57600080fd5b600260c052602060c02001555b81516001018083528114156100a4575b50506112f956600035601c52740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a052600015610277575b6101605261014052600061018052610140516101a0526101c060006008818352015b61018051600860008112156100da578060000360020a82046100e1565b8060020a82025b905090506101805260ff6101a051166101e052610180516101e0516101805101101561010c57600080fd5b6101e0516101805101610180526101a0517ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff86000811215610155578060000360020a820461015c565b8060020a82025b905090506101a0525b81516001018083528114156100bd575b50506018600860208206610200016020828401111561019357600080fd5b60208061022082610180600060046015f15050818152809050905090508051602001806102c0828460006004600a8704601201f16101d057600080fd5b50506102c05160206001820306601f82010390506103206102c0516008818352015b826103205111156102025761021e565b6000610320516102e001535b81516001018083528114156101f2575b50505060206102a05260406102c0510160206001820306601f8201039050610280525b6000610280511115156102535761026f565b602061028051036102a001516020610280510361028052610241565b610160515650005b63863a311b600051141561050857341561029057600080fd5b6000610140526101405161016052600154610180526101a060006020818352015b60016001610180511614156103325760006101a051602081106102d357600080fd5b600060c052602060c02001546020826102400101526020810190506101605160208261024001015260208101905080610240526102409050602060c0825160208401600060025af161032457600080fd5b60c0519050610160526103a0565b6000610160516020826101c00101526020810190506101a0516020811061035857600080fd5b600260c052602060c02001546020826101c0010152602081019050806101c0526101c09050602060c0825160208401600060025af161039657600080fd5b60c0519050610160525b61018060026103ae57600080fd5b60028151048152505b81516001018083528114156102b1575b505060006101605160208261044001015260208101905061014051610160516101805163806732896102c0526001546102e0526102e0516006580161009b565b506103405260006103a0525b6103405160206001820306601f82010390506103a0511015156104355761044e565b6103a05161036001526103a0516020016103a052610413565b61018052610160526101405261034060088060208461044001018260208501600060046012f150508051820191505060006018602082066103c0016020828401111561049957600080fd5b6020806103e082610140600060046015f150508181528090509050905060188060208461044001018260208501600060046014f150508051820191505080610440526104409050602060c0825160208401600060025af16104f957600080fd5b60c051905060005260206000f3005b63621fd130600051141561061a57341561052157600080fd5b63806732896101405260015461016052610160516006580161009b565b506101c0526000610220525b6101c05160206001820306601f82010390506102205110151561056c57610585565b610220516101e00152610220516020016102205261054a565b6101c0805160200180610280828460006004600a8704601201f16105a857600080fd5b50506102805160206001820306601f82010390506102e0610280516008818352015b826102e05111156105da576105f6565b60006102e0516102a001535b81516001018083528114156105ca575b5050506020610260526040610280510160206001820306601f8201039050610260f3005b63c47e300d600051141561117457606060046101403760506004356004016101a037603060043560040135111561065057600080fd5b604060243560040161022037602060243560040135111561067057600080fd5b608060443560040161028037606060443560040135111561069057600080fd5b63ffffffff600154106106a257600080fd5b633b9aca0061034052610340516106b857600080fd5b61034051340461032052633b9aca006103205110156106d657600080fd5b60306101a051146106e657600080fd5b602061022051146106f657600080fd5b6060610280511461070657600080fd5b6101a0516101c0516101e05161020051610220516102405161026051610280516102a0516102c0516102e05161030051610320516103405161036051610380516103a05163806732896103c052610320516103e0526103e0516006580161009b565b506104405260006104a0525b6104405160206001820306601f82010390506104a051101515610796576107af565b6104a05161046001526104a0516020016104a052610774565b6103a05261038052610360526103405261032052610300526102e0526102c0526102a05261028052610260526102405261022052610200526101e0526101c0526101a052610440805160200180610360828460006004600a8704601201f161081657600080fd5b50506101a0516101c0516101e05161020051610220516102405161026051610280516102a0516102c0516102e05161030051610320516103405161036051610380516103a0516103c0516103e05161040051610420516104405161046051610480516104a05163806732896104c0526001546104e0526104e0516006580161009b565b506105405260006105a0525b6105405160206001820306601f82010390506105a0511015156108c7576108e0565b6105a05161056001526105a0516020016105a0526108a5565b6104a05261048052610460526104405261042052610400526103e0526103c0526103a05261038052610360526103405261032052610300526102e0526102c0526102a05261028052610260526102405261022052610200526101e0526101c0526101a0526105408051602001806105c0828460006004600a8704601201f161096757600080fd5b505060a06106405261064051610680526101a08051602001806106405161068001828460006004600a8704601201f161099f57600080fd5b505061064051610680015160206001820306601f8201039050610640516106800161062081516040818352015b83610620511015156109dd576109fa565b6000610620516020850101535b81516001018083528114156109cc575b50505050602061064051610680015160206001820306601f820103905061064051010161064052610640516106a0526102208051602001806106405161068001828460006004600a8704601201f1610a5157600080fd5b505061064051610680015160206001820306601f8201039050610640516106800161062081516020818352015b8361062051101515610a8f57610aac565b6000610620516020850101535b8151600101808352811415610a7e575b50505050602061064051610680015160206001820306601f820103905061064051010161064052610640516106c0526103608051602001806106405161068001828460006004600a8704601201f1610b0357600080fd5b505061064051610680015160206001820306601f8201039050610640516106800161062081516020818352015b8361062051101515610b4157610b5e565b6000610620516020850101535b8151600101808352811415610b30575b50505050602061064051610680015160206001820306601f820103905061064051010161064052610640516106e0526102808051602001806106405161068001828460006004600a8704601201f1610bb557600080fd5b505061064051610680015160206001820306601f8201039050610640516106800161062081516060818352015b8361062051101515610bf357610c10565b6000610620516020850101535b8151600101808352811415610be2575b50505050602061064051610680015160206001820306601f82010390506106405101016106405261064051610700526105c08051602001806106405161068001828460006004600a8704601201f1610c6757600080fd5b505061064051610680015160206001820306601f8201039050610640516106800161062081516020818352015b8361062051101515610ca557610cc2565b6000610620516020850101535b8151600101808352811415610c94575b50505050602061064051610680015160206001820306601f8201039050610640510101610640527f649bbc62d0e31342afea4e5cd82d4049e7e1ee912fc0889aa790803be39038c561064051610680a160006107205260006101a06030806020846107e001018260208501600060046016f150508051820191505060006010602082066107600160208284011115610d5957600080fd5b60208061078082610720600060046015f15050818152809050905090506010806020846107e001018260208501600060046013f1505080518201915050806107e0526107e09050602060c0825160208401600060025af1610db957600080fd5b60c0519050610740526000600060406020820661088001610280518284011115610de257600080fd5b6060806108a0826020602088068803016102800160006004601bf1505081815280905090509050602060c0825160208401600060025af1610e2257600080fd5b60c0519050602082610a800101526020810190506000604060206020820661094001610280518284011115610e5657600080fd5b606080610960826020602088068803016102800160006004601bf1505081815280905090509050602080602084610a0001018260208501600060046015f150508051820191505061072051602082610a0001015260208101905080610a0052610a009050602060c0825160208401600060025af1610ed357600080fd5b60c0519050602082610a8001015260208101905080610a8052610a809050602060c0825160208401600060025af1610f0a57600080fd5b60c0519050610860526000600061074051602082610b20010152602081019050610220602080602084610b2001018260208501600060046015f150508051820191505080610b2052610b209050602060c0825160208401600060025af1610f7057600080fd5b60c0519050602082610ca00101526020810190506000610360600880602084610c2001018260208501600060046012f15050805182019150506000601860208206610ba00160208284011115610fc557600080fd5b602080610bc082610720600060046015f1505081815280905090509050601880602084610c2001018260208501600060046014f150508051820191505061086051602082610c2001015260208101905080610c2052610c209050602060c0825160208401600060025af161103857600080fd5b60c0519050602082610ca001015260208101905080610ca052610ca09050602060c0825160208401600060025af161106f57600080fd5b60c0519050610b0052600180546001825401101561108c57600080fd5b6001815401815550600154610d2052610d4060006020818352015b60016001610d20511614156110dc57610b0051610d4051602081106110cb57600080fd5b600060c052602060c0200155611170565b6000610d4051602081106110ef57600080fd5b600060c052602060c0200154602082610d60010152602081019050610b0051602082610d6001015260208101905080610d6052610d609050602060c0825160208401600060025af161114057600080fd5b60c0519050610b0052610d20600261115757600080fd5b60028151048152505b81516001018083528114156110a7575b5050005b60006000fd5b61017f6112f90361017f60003961017f6112f9036000f3";
  BigInteger gweiAmount = BigInteger.valueOf(32L * 1_000_000_000L);
  BigInteger depositAmount = gweiAmount.multiply(BigInteger.valueOf(1_000_000_000L));

  private BeaconChainSpec createSpec() {
    return new BeaconChainSpec.Builder()
        .withDefaultHashFunction()
        .withBlsVerifyProofOfPossession(false)
        .withConstants(
            new SpecConstants() {
              @Override
              public UInt64 getMinGenesisActiveValidatorCount() {
                return UInt64.valueOf(16);
              }

              @Override
              public Time getSecondsPerDay() {
                return Time.of(5);
              }

              @Override
              public Time getMinGenesisTime() {
                return Time.of(0);
              }
            })
        .withDefaultHasher()
        .build();
  }

  @Test
  public void test1() {
    StandaloneBlockchain sb = new StandaloneBlockchain().withAutoblock(true);
    ContractMetadata contractMetadata = new ContractMetadata();
    contractMetadata.abi = ContractAbi.getContractAbi();
    contractMetadata.bin = depositBin;
    SolidityContract contract = sb.submitNewContract(contractMetadata);
    Object[] depositRoot = contract.callConstFunction("get_hash_tree_root");
    System.out.println(Hex.encodeHexString((byte[]) depositRoot[0]));

    for (int i = 0; i < 20; i++) {
      MutableBytes48 pubKey = MutableBytes48.create();
      pubKey.set(0, (byte) i);

      SolidityCallResult result =
          contract.callFunction(
              depositAmount,
              "deposit",
              pubKey.extractArray(),
              Hash32.ZERO.extractArray(),
              Bytes96.ZERO.extractArray());

      Assert.assertTrue(result.isSuccessful());
      Assert.assertEquals(1, result.getEvents().size());
    }

    for (int i = 0; i < 16; i++) {
      sb.createBlock();
    }

    Ethereum ethereum = new StandaloneEthereum(new SystemProperties(), sb);
    EthereumJDepositContract depositContract =
        new EthereumJDepositContract(
            ethereum,
            0,
            BytesValue.wrap(contract.getAddress()).toString(),
            Schedulers.createDefault(),
            HASH_FUNCTION,
            MERKLE_TREE_DEPTH,
            createSpec());
    depositContract.setDistanceFromHead(3);

    ChainStart chainStart =
        Mono.from(depositContract.getChainStartMono()).block(Duration.ofSeconds(60));

    Assert.assertEquals(16, chainStart.getInitialDeposits().size());
    Assert.assertEquals(
        17,
        sb.getBlockchain()
            .getBlockByHash(chainStart.getEth1Data().getBlockHash().extractArray())
            .getNumber());
    for (int i = 0; i < 16; i++) {
      Assert.assertEquals(
          (byte) i, chainStart.getInitialDeposits().get(i).getData().getPubKey().get(0));
    }

    depositRoot = contract.callConstFunction("get_hash_tree_root");
    System.out.println(Hex.encodeHexString((byte[]) depositRoot[0]));

    Eth1Data lastDepositEthData =
        new Eth1Data(
            Hash32.wrap(Bytes32.wrap((byte[]) depositRoot[0])),
            UInt64.ZERO,
            Hash32.wrap(Bytes32.wrap(sb.getBlockchain().getBlockByNumber(21).getHash())));

    List<DepositInfo> depositInfos1 =
        depositContract.peekDeposits(2, chainStart.getEth1Data(), lastDepositEthData);

    Assert.assertEquals(2, depositInfos1.size());
    Assert.assertEquals((byte) 16, depositInfos1.get(0).getDeposit().getData().getPubKey().get(0));
    Assert.assertEquals((byte) 17, depositInfos1.get(1).getDeposit().getData().getPubKey().get(0));

    List<DepositInfo> depositInfos2 =
        depositContract.peekDeposits(200, depositInfos1.get(1).getEth1Data(), lastDepositEthData);

    Assert.assertEquals(2, depositInfos2.size());
    Assert.assertEquals((byte) 18, depositInfos2.get(0).getDeposit().getData().getPubKey().get(0));
    Assert.assertEquals((byte) 19, depositInfos2.get(1).getDeposit().getData().getPubKey().get(0));

    List<DepositInfo> depositInfos3 =
        depositContract.peekDeposits(200, lastDepositEthData, lastDepositEthData);
    Assert.assertEquals(0, depositInfos3.size());
  }

  @Test
  public void testOnline() {
    StandaloneBlockchain sb = new StandaloneBlockchain();
    ContractMetadata contractMetadata = new ContractMetadata();
    contractMetadata.abi = ContractAbi.getContractAbi();
    contractMetadata.bin = depositBin;
    SolidityContract contract = sb.submitNewContract(contractMetadata);
    sb.createBlock();

    Ethereum ethereum = new StandaloneEthereum(new SystemProperties(), sb);
    EthereumJDepositContract depositContract =
        new EthereumJDepositContract(
            ethereum,
            0,
            BytesValue.wrap(contract.getAddress()).toString(),
            Schedulers.createDefault(),
            HASH_FUNCTION,
            MERKLE_TREE_DEPTH,
            createSpec());
    depositContract.setDistanceFromHead(3);
    Mono<ChainStart> chainStartMono = Mono.from(depositContract.getChainStartMono());
    chainStartMono.subscribe();

    for (int i = 0; i < 16; i++) {
      sb.createBlock();
    }

    for (int i = 0; i < 16; i++) {
      MutableBytes48 pubKey = MutableBytes48.create();
      pubKey.set(0, (byte) i);

      SolidityCallResult result =
          contract.callFunction(
              depositAmount,
              "deposit",
              pubKey.extractArray(),
              Hash32.ZERO.extractArray(),
              Bytes96.ZERO.extractArray());
      sb.createBlock();
      sb.createBlock();

      Assert.assertTrue(result.isSuccessful());
      Assert.assertEquals(1, result.getEvents().size());
    }

    Assert.assertFalse(chainStartMono.toFuture().isDone());

    sb.createBlock();
    sb.createBlock();
    sb.createBlock();

    ChainStart chainStart = chainStartMono.block(Duration.ofSeconds(1));

    Assert.assertEquals(16, chainStart.getInitialDeposits().size());
    Assert.assertEquals(
        1 + 16 + 31,
        sb.getBlockchain()
            .getBlockByHash(chainStart.getEth1Data().getBlockHash().extractArray())
            .getNumber());
    for (int i = 0; i < 16; i++) {
      Assert.assertEquals(
          (byte) i, chainStart.getInitialDeposits().get(i).getData().getPubKey().get(0));
    }

    Optional<Eth1Data> latestEth1Data1 = depositContract.getLatestEth1Data();
    Assert.assertTrue(latestEth1Data1.isPresent());
    Assert.assertEquals(chainStart.getEth1Data(), latestEth1Data1.get());

    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        MutableBytes48 pubKey = MutableBytes48.create();
        pubKey.set(0, (byte) (0x20 + i * 4 + j));

        contract.callFunction(
            depositAmount,
            "deposit",
            pubKey.extractArray(),
            Hash32.ZERO.extractArray(),
            Bytes96.ZERO.extractArray());
      }
      sb.createBlock();
      sb.createBlock();
    }

    Optional<Eth1Data> latestEth1Data2 = depositContract.getLatestEth1Data();
    Assert.assertTrue(latestEth1Data2.isPresent());
    Assert.assertEquals(
        ethereum.getBlockchain().getBestBlock().getNumber() - 3,
        ethereum
            .getBlockchain()
            .getBlockByHash(latestEth1Data2.get().getBlockHash().extractArray())
            .getNumber());

    sb.createBlock();
    sb.createBlock();
    sb.createBlock();
    sb.createBlock();

    Optional<Eth1Data> latestEth1Data3 = depositContract.getLatestEth1Data();
    Assert.assertTrue(latestEth1Data3.isPresent());
    Assert.assertNotEquals(latestEth1Data2, latestEth1Data3);

    List<DepositInfo> allDepos = new ArrayList<>();
    Eth1Data from = chainStart.getEth1Data();
    while (true) {
      List<DepositInfo> infos = depositContract.peekDeposits(3, from, latestEth1Data3.get());
      if (infos.isEmpty()) break;
      allDepos.addAll(infos);
      from = infos.get(infos.size() - 1).getEth1Data();
    }
    Assert.assertEquals(16, allDepos.size());
    for (int i = 0; i < 16; i++) {
      Assert.assertEquals(0x20 + i, allDepos.get(i).getDeposit().getData().getPubKey().get(0));
    }
  }

  @Test
  public void testVerifyDepositRoot() throws InterruptedException {
    StandaloneBlockchain sb = new StandaloneBlockchain();
    ContractMetadata contractMetadata = new ContractMetadata();
    contractMetadata.abi = ContractAbi.getContractAbi();
    contractMetadata.bin = depositBin;
    SolidityContract contract = sb.submitNewContract(contractMetadata);
    sb.createBlock();

    Ethereum ethereum = new StandaloneEthereum(new SystemProperties(), sb);
    byte[] latestCalculatedDepositRoot = new byte[32];
    EthereumJDepositContract depositContract =
        new EthereumJDepositContract(
            ethereum,
            0,
            BytesValue.wrap(contract.getAddress()).toString(),
            Schedulers.createDefault(),
            HASH_FUNCTION,
            MERKLE_TREE_DEPTH,
            createSpec()) {

          // avoid async block processing
          @Override
          protected void processConfirmedBlocks() {
            long bestConfirmedBlock = getBestConfirmedBlock();
            processBlocksUpTo(bestConfirmedBlock);
          }

          @Override
          protected synchronized void newDeposits(
              List<DepositEventData> eventDataList, byte[] blockHash, long blockTimestamp) {
            super.newDeposits(eventDataList, blockHash, blockTimestamp);
            for (DepositEventData eventData : eventDataList) {
              System.arraycopy(
                  getDepositRoot(eventData.index).extractArray(),
                  0,
                  latestCalculatedDepositRoot,
                  0,
                  32);
            }
          }
        };
    depositContract.setDistanceFromHead(3);
    Mono<ChainStart> chainStartMono = Mono.from(depositContract.getChainStartMono());
    chainStartMono.subscribe();

    for (int i = 0; i < 20; i++) {
      MutableBytes48 pubKey = MutableBytes48.create();
      pubKey.set(0, (byte) i);

      SolidityCallResult result =
          contract.callFunction(
              depositAmount,
              "deposit",
              pubKey.extractArray(),
              Hash32.ZERO.extractArray(),
              Bytes96.ZERO.extractArray());
      sb.createBlock();
      sb.createBlock();
      sb.createBlock();
      sb.createBlock();

      Object[] depositRoot = contract.callConstFunction("get_hash_tree_root");
      Assert.assertArrayEquals((byte[]) depositRoot[0], latestCalculatedDepositRoot);

      Assert.assertTrue(result.isSuccessful());
      Assert.assertEquals(1, result.getEvents().size());
    }
  }

  @Test
  public void testVerifyProofs() {
    StandaloneBlockchain sb = new StandaloneBlockchain().withAutoblock(true);
    ContractMetadata contractMetadata = new ContractMetadata();
    contractMetadata.abi = ContractAbi.getContractAbi();
    contractMetadata.bin = depositBin;
    SolidityContract contract = sb.submitNewContract(contractMetadata);
    Object[] depositRoot = contract.callConstFunction("get_hash_tree_root");
    System.out.println(Hex.encodeHexString((byte[]) depositRoot[0]));

    BlsKeyPairGenerator generator = BlsKeyPairGenerator.createWithoutSeed();
    BeaconChainSpec spec = createSpec();
    List<Eth1Data> eth1DataList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      BLS381.KeyPair keyPair = generator.next();
      Bytes48 pubKey = keyPair.getPublic().getEncodedBytes();
      Hash32 withdrawalCredentials =
          Hash32.wrap(Bytes32.leftPad(UInt64.valueOf(i).toBytesBigEndian()));
      DepositData depositData =
          new DepositData(
              BLSPubkey.wrap(pubKey),
              withdrawalCredentials,
              Gwei.castFrom(UInt64.valueOf(gweiAmount.longValue())),
              BLSSignature.ZERO);
      // Let signature be the result of bls_sign of the signing_root(deposit_data) with
      // domain=DOMAIN_DEPOSIT.
      MessageParameters messageParameters =
          MessageParameters.create(spec.signing_root(depositData), SignatureDomains.DEPOSIT);
      BLS381.Signature signature = BLS381.sign(messageParameters, keyPair);

      SolidityCallResult result =
          contract.callFunction(
              depositAmount,
              "deposit",
              pubKey.extractArray(),
              withdrawalCredentials.extractArray(),
              signature.getEncoded().extractArray());

      Assert.assertTrue(result.isSuccessful());

      depositRoot = contract.callConstFunction("get_hash_tree_root");
      Object[] depositCount = contract.callConstFunction("get_deposit_count");
      Eth1Data lastDepositEthData =
          new Eth1Data(
              Hash32.wrap(Bytes32.wrap((byte[]) depositRoot[0])),
              UInt64.fromBytesLittleEndian(Bytes8.wrap((byte[]) depositCount[0])),
              Hash32.ZERO);
      eth1DataList.add(lastDepositEthData);

      System.out.println(
          String.format(
              "root %d: %s",
              lastDepositEthData.getDepositCount().decrement().getValue(),
              lastDepositEthData.getDepositRoot()));

      Assert.assertEquals(1, result.getEvents().size());
    }

    for (int i = 0; i < 16; i++) {
      sb.createBlock();
    }

    Ethereum ethereum = new StandaloneEthereum(new SystemProperties(), sb);
    EthereumJDepositContract depositContract =
        new EthereumJDepositContract(
            ethereum,
            0,
            BytesValue.wrap(contract.getAddress()).toString(),
            Schedulers.createDefault(),
            HASH_FUNCTION,
            MERKLE_TREE_DEPTH,
            spec);
    depositContract.setDistanceFromHead(3);

    ChainStart chainStart =
        Mono.from(depositContract.getChainStartMono()).block(Duration.ofSeconds(2));

    Assert.assertEquals(16, chainStart.getInitialDeposits().size());
    MutableBeaconState beaconState = BeaconState.getEmpty().createMutableCopy();
    beaconState.setEth1Data(chainStart.getEth1Data());
    for (Deposit deposit : chainStart.getInitialDeposits()) {
      //       The proof for each deposit must be constructed against the deposit root contained in
      // state.latest_eth1_data rather than the deposit root at the time the deposit was initially
      // logged from the 1.0 chain. This entails storing a full deposit merkle tree locally and
      // computing updated proofs against the latest_eth1_data.deposit_root as needed. See
      // minimal_merkle.py for a sample implementation.
      spec.verify_deposit(beaconState, deposit);
      spec.process_deposit(beaconState, deposit);
    }

    depositRoot = contract.callConstFunction("get_hash_tree_root");
    System.out.println(Hex.encodeHexString((byte[]) depositRoot[0]));

    Eth1Data lastDepositEthData =
        new Eth1Data(
            Hash32.wrap(Bytes32.wrap((byte[]) depositRoot[0])),
            UInt64.ZERO,
            Hash32.wrap(Bytes32.wrap(sb.getBlockchain().getBlockByNumber(21).getHash())));

    List<DepositInfo> depositInfos1 =
        depositContract.peekDeposits(100, chainStart.getEth1Data(), lastDepositEthData);

    Assert.assertEquals(4, depositInfos1.size());
    for (DepositInfo depositInfo : depositInfos1) {
      beaconState.setEth1Data(
          eth1DataList.get(depositInfo.getEth1Data().getDepositCount().decrement().intValue()));
      spec.verify_deposit(beaconState, depositInfo.getDeposit());
      spec.process_deposit(beaconState, depositInfo.getDeposit());
    }
  }

  @Test
  public void testVyperAbi() {
    String abiTestBin =
        "0x6101db56600035601c52740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a05263d45754f860005114156101d157602060046101403760606004356004016101603760406004356004013511156100c957600080fd5b7f11111111111111111111111111111111111111111111111111111111111111116102405260406102005261020051610260526101608051602001806102005161024001828460006004600a8704601201f161012457600080fd5b505061020051610240015160206001820306601f820103905061020051610240016101e081516040818352015b836101e0511015156101625761017f565b60006101e0516020850101535b8151600101808352811415610151575b50505050602061020051610240015160206001820306601f8201039050610200510101610200527f68ab17451419beb01e059af9ee2a11c36d17b75ed25144e5cf78a0a469883ed161020051610240a1005b60006000fd5b6100046101db036100046000396100046101db036000f3";
    String abiTestAbi =
        "[{\"name\": \"Deposit\", \"inputs\": [{\"type\": \"bytes32\", \"name\": \"deposit_root\", \"indexed\": false}, {\"type\": \"bytes\", \"name\": \"data\", \"indexed\": false}, {\"type\": \"bytes\", \"name\": \"merkle_tree_index\", \"indexed\": false}, {\"type\": \"bytes32[32]\", \"name\": \"branch\", \"indexed\": false}], \"anonymous\": false, \"type\": \"event\"}, {\"name\": \"ChainStart\", \"inputs\": [{\"type\": \"bytes32\", \"name\": \"deposit_root\", \"indexed\": false}, {\"type\": \"bytes\", \"name\": \"time\", \"indexed\": false}], \"anonymous\": false, \"type\": \"event\"}, {\"name\": \"Test\", \"inputs\": [{\"type\": \"bytes32\", \"name\": \"a\", \"indexed\": false}, {\"type\": \"bytes\", \"name\": \"data\", \"indexed\": false}], \"anonymous\": false, \"type\": \"event\"}, {\"name\": \"__init__\", \"outputs\": [], \"inputs\": [], \"constant\": false, \"payable\": false, \"type\": \"constructor\"}, {\"name\": \"get_deposit_root\", \"outputs\": [{\"type\": \"bytes32\", \"name\": \"out\"}], \"inputs\": [], \"constant\": true, \"payable\": false, \"type\": \"function\", \"gas\": 30775}, {\"name\": \"f\", \"outputs\": [], \"inputs\": [{\"type\": \"bytes\", \"name\": \"a\"}], \"constant\": false, \"payable\": true, \"type\": \"function\", \"gas\": 49719}, {\"name\": \"deposit\", \"outputs\": [], \"inputs\": [{\"type\": \"bytes\", \"name\": \"deposit_input\"}], \"constant\": false, \"payable\": true, \"type\": \"function\", \"gas\": 637708}]\n";
    StandaloneBlockchain sb = new StandaloneBlockchain().withAutoblock(true);
    ContractMetadata contractMetadata = new ContractMetadata();
    contractMetadata.abi = abiTestAbi.replaceAll(", *\"gas\": *[0-9]+", "");
    contractMetadata.bin = abiTestBin.replace("0x", "");
    SolidityContract contract = sb.submitNewContract(contractMetadata);
    ((SolidityContractImpl) contract)
        .addRelatedContract(ContractAbi.getContractAbi()); // TODO ethJ bug workaround

    byte[] bytes = new byte[64];
    Arrays.fill(bytes, (byte) 0x33);

    SolidityCallResult result = contract.callFunction("f", (Object) bytes);

    Object[] returnValues = result.getEvents().get(0).args;
    System.out.println(result);
  }
}

package pegasys.artemis.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.io.Resources;
import org.junit.jupiter.params.provider.Arguments;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class BeaconStateTestHelper {

    public static BeaconState convertMapToBeaconState(LinkedHashMap<String, Object> map){
        UnsignedLong slot = UnsignedLong.valueOf(map.get("slot").toString());
        UnsignedLong genesis_time = UnsignedLong.valueOf(map.get("genesis_time").toString());
        Fork fork = mapToFork((Map<String, Object>)map.get("fork"));
        List<Validator> validator_registry = mapToValidatorRegistry((ArrayList<Object>)map.get("validator_registry"));
        List<UnsignedLong> balances = toListUnsignedLong((ArrayList<Object>)map.get("balances"));
        List<Bytes32> latest_randao_mixes = toListBytes32((ArrayList<Object>)map.get("latest_randao_mixes"));
        UnsignedLong latest_start_shard = UnsignedLong.valueOf(map.get("latest_start_shard").toString());
        List<PendingAttestation> previous_epoch_attestations = mapToPendingAttestations((ArrayList<Object>)map.get("previous_epoch_attestations"));
        List<PendingAttestation> current_epoch_attestations = mapToPendingAttestations((ArrayList<Object>)map.get("current_epoch_attestations"));
        UnsignedLong previous_justified_epoch = UnsignedLong.valueOf(map.get("previous_justified_epoch").toString());
        UnsignedLong current_justified_epoch = UnsignedLong.valueOf(map.get("current_justified_epoch").toString());
        Bytes32 previous_justified_root = Bytes32.fromHexString(map.get("previous_justified_root").toString());
        Bytes32 current_justified_root = Bytes32.fromHexString(map.get("current_justified_root").toString());
        UnsignedLong justification_bitfield = UnsignedLong.valueOf(map.get("justification_bitfield").toString());
        UnsignedLong finalized_epoch = UnsignedLong.valueOf(map.get("finalized_epoch").toString());
        Bytes32 finalized_root = Bytes32.fromHexString(map.get("finalized_root").toString());
        List<Crosslink> current_crosslinks = mapToCrosslinks((ArrayList<Object>)map.get("current_crosslinks"));
        List<Crosslink> previous_crosslinks = mapToCrosslinks((ArrayList<Object>)map.get("previous_crosslinks"));
        List<Bytes32> latest_block_roots = toListBytes32((ArrayList<Object>)map.get("latest_block_roots"));
        List<Bytes32> latest_state_roots = toListBytes32((ArrayList<Object>)map.get("latest_state_roots"));
        List<Bytes32> latest_active_index_roots = toListBytes32((ArrayList<Object>)map.get("latest_active_index_roots"));
        List<UnsignedLong> latest_slashed_balances = toListUnsignedLong((ArrayList<Object>)map.get("latest_slashed_balances"));
        BeaconBlockHeader latest_block_header = mapToBeaconBlockHeader((Map<String, Object>)map.get("latest_block_header"));
        List<Bytes32> historical_roots = toListBytes32((ArrayList<Object>)map.get("historical_roots"));
        Eth1Data latest_eth1_data = mapToEth1Data((Map<String, Object>)map.get("latest_eth1_data"));
        List<Eth1Data> eth1_data_votes = mapToEth1DataVotes((ArrayList<Object>)map.get("eth1_data_votes"));
        UnsignedLong deposit_index = UnsignedLong.valueOf(map.get("deposit_index").toString());
        return new BeaconState(
                slot,
                genesis_time,
                fork,
                validator_registry,
                balances,
                latest_randao_mixes,
                latest_start_shard,
                previous_epoch_attestations,
                current_epoch_attestations,
                previous_justified_epoch,
                current_justified_epoch,
                previous_justified_root,
                current_justified_root,
                justification_bitfield,
                finalized_epoch,
                finalized_root,
                current_crosslinks,
                previous_crosslinks,
                latest_block_roots,
                latest_state_roots,
                latest_active_index_roots,
                latest_slashed_balances,
                latest_block_header,
                historical_roots,
                latest_eth1_data,
                eth1_data_votes,
                deposit_index
        );
    }

    private static BeaconBlockHeader mapToBeaconBlockHeader(Map<String, Object> map) {
        UnsignedLong slot = UnsignedLong.valueOf(map.get("slot").toString());
        Bytes32 parent_root = Bytes32.fromHexString(map.get("parent_root").toString());
        Bytes32 state_root = Bytes32.fromHexString(map.get("state_root").toString());
        Bytes32 body_root = Bytes32.fromHexString(map.get("body_root").toString());
        BLSSignature signature = BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString()));

        return new BeaconBlockHeader(
                slot,
                parent_root,
                state_root,
                body_root,
                signature
        );
    }

    private static List<Eth1Data> mapToEth1DataVotes(ArrayList<Object> map) {
        List<Eth1Data> eth1_data_votes = new ArrayList<Eth1Data>();
        Iterator<Object> itr = map.iterator();
        while(itr.hasNext()){
            Map<String, Object> obj = (Map<String, Object>)itr.next();
            eth1_data_votes.add(mapToEth1Data(obj));
        }
        return eth1_data_votes;
    }

    private static Eth1Data mapToEth1Data(Map<String, Object> obj) {
        Bytes32 deposit_root = Bytes32.fromHexString(obj.get("deposit_root").toString());
        UnsignedLong deposit_count =  UnsignedLong.valueOf(obj.get("deposit_count").toString());
        Bytes32 block_hash = Bytes32.fromHexString(obj.get("block_hash").toString());

        return new Eth1Data(
                deposit_root,
                deposit_count,
                block_hash);
    }

    private static List<Crosslink> mapToCrosslinks(ArrayList<Object> crosslinks_map) {
        List<Crosslink> crosslinks = new ArrayList<Crosslink>();
        Iterator<Object> itr = crosslinks_map.iterator();
        while(itr.hasNext()){
            Map<String, Object> obj = (Map<String, Object>)itr.next();
            crosslinks.add(mapToCrosslink(obj));
        }

        return crosslinks;
    }

    private static List<PendingAttestation> mapToPendingAttestations(ArrayList<Object> pending_attestations_map) {
        List<PendingAttestation> pending_attestations = new ArrayList<PendingAttestation>();
        Iterator<Object> itr = pending_attestations_map.iterator();
        while(itr.hasNext()){
            Map<String, Object> obj = (Map<String, Object>)itr.next();
            pending_attestations.add(mapToPendingAttestation(obj));
        }
        return pending_attestations;
    }

    private static PendingAttestation mapToPendingAttestation(Map<String, Object> obj) {
        Bytes aggregation_bitfield =  Bytes.fromHexString(obj.get("aggregation_bitfield").toString());
        AttestationData data = maptoAttestationData((Map<String, Object>)obj.get("data"));
        UnsignedLong inclusion_delay = UnsignedLong.valueOf(obj.get("inclusion_delay").toString());
        UnsignedLong proposer = UnsignedLong.valueOf(obj.get("proposer_index").toString());
        return new PendingAttestation(
                aggregation_bitfield,
                data,
                inclusion_delay,
                proposer
        );
    }

    private static AttestationData maptoAttestationData(Map<String, Object> obj) {
        Bytes32 beacon_block_root =  Bytes32.fromHexString(obj.get("beacon_block_root").toString());
        UnsignedLong source_epoch = UnsignedLong.valueOf(obj.get("source_epoch").toString());
        Bytes32 source_root =  Bytes32.fromHexString(obj.get("source_root").toString());
        UnsignedLong target_epoch = UnsignedLong.valueOf(obj.get("target_epoch").toString());
        Bytes32 target_root =  Bytes32.fromHexString(obj.get("target_root").toString());
        Crosslink crosslink = mapToCrosslink((Map<String, Object>)obj.get("crosslink"));

        return new AttestationData(
                beacon_block_root,
                source_epoch,
                source_root,
                target_epoch,
                target_root,
                crosslink
        );
    }

    private static Crosslink mapToCrosslink(Map<String, Object> obj) {
        UnsignedLong shard = UnsignedLong.valueOf(obj.get("shard").toString());
        UnsignedLong start_epoch = UnsignedLong.valueOf(obj.get("start_epoch").toString());
        UnsignedLong end_epoch = UnsignedLong.valueOf(obj.get("end_epoch").toString());
        Bytes32 parent_root =  Bytes32.fromHexString(obj.get("parent_root").toString());
        Bytes32 data_root =  Bytes32.fromHexString(obj.get("data_root").toString());
        return new Crosslink(
                shard,
                start_epoch,
                end_epoch,
                parent_root,
                data_root);
    }

    private static Fork mapToFork(Map<String, Object> fork){
        Bytes previous_version = Bytes.fromHexString(fork.get("previous_version").toString());
        Bytes current_version = Bytes.fromHexString(fork.get("current_version").toString());
        UnsignedLong epoch = UnsignedLong.valueOf(fork.get("epoch").toString());
        return new Fork(previous_version, current_version, epoch);
    }

    private static List<Validator> mapToValidatorRegistry(List<Object> validators){
        List<Validator> validator_registry = new ArrayList<Validator>();
        Iterator<Object> itr = validators.iterator();
        while(itr.hasNext()){
            Map<String, Object> obj = (Map<String, Object>)itr.next();
            validator_registry.add(mapToValidator(obj));
        }

        return validator_registry;
    }

    private static Validator mapToValidator(Map<String, Object> validator){
        BLSPublicKey pubkey = BLSPublicKey.fromBytesCompressed(Bytes.fromHexString(validator.get("pubkey").toString()));
        Bytes32 withdrawal_credentials = Bytes32.fromHexString(validator.get("withdrawal_credentials").toString());
        UnsignedLong activation_eligibility_epoch = UnsignedLong.valueOf(validator.get("activation_eligibility_epoch").toString());
        UnsignedLong activation_epoch = UnsignedLong.valueOf(validator.get("activation_epoch").toString());
        UnsignedLong exit_epoch = UnsignedLong.valueOf(validator.get("activation_epoch").toString());
        UnsignedLong withdrawable_epoch = UnsignedLong.valueOf(validator.get("withdrawable_epoch").toString());
        boolean slashable = validator.get("slashed").equals("true");
        UnsignedLong effective_balance = UnsignedLong.valueOf(validator.get("effective_balance").toString());
        return new Validator(pubkey, withdrawal_credentials, activation_eligibility_epoch, activation_epoch, exit_epoch, withdrawable_epoch, slashable, effective_balance);
    }

    private static List<UnsignedLong> toListUnsignedLong(List<Object> balancesMap){
        List<UnsignedLong> balances = new ArrayList<UnsignedLong>();
        Iterator<Object> itr = balancesMap.iterator();
        while(itr.hasNext()){
            Object obj = itr.next();
            balances.add(UnsignedLong.valueOf(obj.toString()));
        }
        return balances;
    }

    private static List<Bytes32> toListBytes32(List<Object> latest_randao_mixes_map){
        List<Bytes32> latest_randao_mixes = new ArrayList<Bytes32>();
        Iterator<Object> itr = latest_randao_mixes_map.iterator();
        while(itr.hasNext()){
            Object obj = itr.next();
            latest_randao_mixes.add(Bytes32.fromHexString(obj.toString()));
        }
        return latest_randao_mixes;
    }


    @MustBeClosed
    public static Stream<Arguments> findTests(String glob, String tcase) throws IOException {
        return Resources.find(glob)
                .flatMap(
                        url -> {
                            try (InputStream in = url.openConnection().getInputStream()) {
                                return prepareTests(in, tcase);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Stream<Arguments> prepareTests(InputStream in, String tcase) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Map allTests = mapper.readerFor(Map.class).readValue(in);

        return ((List<Map>) allTests.get(tcase))
                .stream().map(testCase -> Arguments.of(testCase.get("pre"), testCase.get("post")));
    }
}

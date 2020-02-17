# Rest Api Notes

## Resources

* [Prysm API]("https://api.prylabs.net/#")
* [Lighthouse API]("https://lighthouse-book.sigmaprime.io/http.html")
* [eth2 specs]("https://github.com/ethereum/eth2.0-specs")
* [eth2-api]("https://ethereum.github.io/eth2.0-APIs/") - [github]("https://github.com/ethereum/eth2.0-APIs")

## /node/genesis_time

In [eth2-api]("https://github.com/ethereum/eth2.0-specs") genesis_time should either return
* 200 code and a numeric
* 500 internal error

Any time a beacon node is in a pre-genesis state, it will be a valid condition to have no
genesis time set. For this reason, it was deemed more appropriate to return:
* 200 code and a numeric
* 204 (no content)

## /beacon/state

In [Lighthouse]("https://lighthouse-book.sigmaprime.io/http_beacon.html#beaconstate") 
and [eth2-api]("https://github.com/ethereum/eth2.0-APIs/blob/master/apis/beacon/basic.md#beacon-state"), 
the beacon state can be queried by slot or root, and only root search is currently implemented in Teku.
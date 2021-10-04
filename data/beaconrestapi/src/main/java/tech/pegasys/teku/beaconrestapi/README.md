# Rest Api Notes

## Resources

* [eth2-spec](https://github.com/ethereum/eth2.0-specs)
* [eth2-api](https://github.com/ethereum/eth2.0-APIs)
* [Prysm API](https://docs.prylabs.network/docs/how-prysm-works/ethereum-public-api)
* [Lighthouse API](https://lighthouse-book.sigmaprime.io/api-bn.html)

## /node/genesis_time

In [eth2-spec](https://github.com/ethereum/eth2.0-specs) genesis_time should either return
* 200 code and a numeric
* 500 internal error

Any time a beacon node is in a pre-genesis state, it will be a valid condition to have no
genesis time set. For this reason, it was deemed more appropriate to return:
* 200 code and a numeric
* 204 (no content)

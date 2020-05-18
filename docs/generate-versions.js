'use strict';

const fs = require('fs');
const args = require('minimist')(process.argv.slice(2));

const TAG = args.tag;
const SPEC = args.spec;

const TARGET_SPEC = `${TAG == '' ? 'latest' : TAG}.json`;

// copy of spec file and rename
fs.copyFile(SPEC, TARGET_SPEC, (err) => {
  if (err) throw err;
  console.log(`${SPEC} copied to ${TARGET_SPEC}`);
});

const JSON_FILE = './versions.json';
let versions = require(JSON_FILE);

if(TAG != ''){
  versions[TAG] = { spec: TAG, source: TAG };
  versions['stable'] = { spec: TAG, source: TAG };
  fs.writeFileSync(JSON_FILE, JSON.stringify(versions,null,1));
}

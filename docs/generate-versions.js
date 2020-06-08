'use strict'

const fs = require('fs')
const args = require('minimist')(process.argv.slice(2))

const tag = args.tag
const spec = args.spec

const targetSpec = `${tag ? tag : 'latest'}.json`

// copy of spec file and rename
fs.copyFile(spec, targetSpec, (err) => {
  if (err) throw err;
  console.log(`${spec} copied to ${targetSpec}`)
});

const versionsJSONFile = './versions.json'
let versions = require(versionsJSONFile)

if(tag){
  versions[tag] = { spec: tag, source: tag };
  versions['stable'] = { spec: tag, source: tag };
  fs.writeFileSync(versionsJSONFile, JSON.stringify(versions,null,1));
}

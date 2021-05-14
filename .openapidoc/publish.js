const fs = require("fs");
const fetch = require("node-fetch");
const ghpages = require("gh-pages");

const config = require("./config.js");

const log = (...args) => console.log(...args); // eslint-disable-line no-console

/**
 * Main function to prepare and publish openapi spec to gh-pages branch
 */
async function main() {
  try {
    const cfg = config.getConfig();
    const {distDir, specs, versions, ghPagesConfig} = cfg;

    prepareDistDir(distDir);

    specs.forEach(function (spec) {
      copySpecFileToDist(spec);
    });

    if (specs[0].isReleaseVersion) {
      const versionsJson = await fetchVersions(versions.url);
      const updatedVersionsJson = updateVersions(
          versionsJson,
          specs[0].version
      );
      saveVersionsJson(updatedVersionsJson, versions.dist);
    }

    log("Publishing following files: ");
    fs.readdirSync(distDir).forEach((file) => {
      log(file);
    });

    cleanGhPagesCache();
    await publishToGHPages(distDir, ghPagesConfig);
    log(
        `OpenAPI specs [${specs[0].version}] published to [${ghPagesConfig.branch}] using user [${ghPagesConfig.user.name}]`
    );
  } catch (err) {
    log(`ERROR: OpenAPI spec failed to publish: ${err.message}`);
    log(config);
    process.exit(1);
  }
}

/**
 * Re-create dist dir
 * @param {string} dirPath
 */
function prepareDistDir(dirPath) {
  fs.rmdirSync(dirPath, {recursive: true});
  fs.mkdirSync(dirPath, {recursive: true});
}

function copySpecFileToDist(spec) {
  fs.copyFileSync(spec.path, spec.latestDist);
  fs.copyFileSync(spec.path, spec.latestDistCompat);
  if (spec.isReleaseVersion) {
    fs.copyFileSync(spec.path, spec.releaseDist);
  }
}

/**
 * Fetch versions.json
 */
async function fetchVersions(versionsUrl) {
  const response = await fetch(versionsUrl);
  if (response.ok) {
    const versionsJson = await response.json();
    return versionsJson;
  }

  throw new Error(
      `${versionsUrl} fetch failed with status: ${response.statusText}`
  );
}

/**
 * update versions
 * @param versionsJson
 * @param {string} specVersion
 */
function updateVersions(versionsJson, specVersion) {
  versionsJson[specVersion] = {
    spec: specVersion,
    source: specVersion,
  };
  versionsJson["stable"] = {
    spec: specVersion,
    source: specVersion,
  };
  return versionsJson;
}

function saveVersionsJson(versionsJson, versionsDist) {
  fs.writeFileSync(versionsDist, JSON.stringify(versionsJson, null, 1));
}

function cleanGhPagesCache() {
  ghpages.clean();
}

/**
 * Publish dist folder to gh-pages branch
 */
async function publishToGHPages(distDir, ghPagesConfig) {
  return new Promise((resolve, reject) => {
    ghpages.publish(distDir, ghPagesConfig, (err) => {
      if (err) {
        reject(err);
      }
      resolve();
    });
  });
}

// start execution of main method
main();

#!/bin/bash
set -euo pipefail

TEKU_VERSION=${1:?Must specify teku version}
TAR_DIST=${2:?Must specify path to tar distribution}
ZIP_DIST=${3:?Must specify path to zip distribution}

ENV_DIR=./build/tmp/cloudsmith-env
if [[ -d ${ENV_DIR} ]] ; then
    source ${ENV_DIR}/bin/activate
else
    python3 -m venv ${ENV_DIR}
    source ${ENV_DIR}/bin/activate
fi

python3 -m pip install --upgrade cloudsmith-cli

cloudsmith push raw consensys/teku $TAR_DIST --version "${TEKU_VERSION}" --summary "Teku ${TEKU_VERSION} binary distribution" --description "Binary distribution of Teku ${TEKU_VERSION}." --content-type 'application/tar+gzip'
cloudsmith push raw consensys/teku $ZIP_DIST --version "${TEKU_VERSION}" --summary "Teku ${TEKU_VERSION} binary distribution" --description "Binary distribution of Teku ${TEKU_VERSION}." --content-type 'application/zip'
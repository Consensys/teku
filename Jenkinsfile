#!/usr/bin/env groovy

import hudson.model.Result
import hudson.model.Run
import jenkins.model.CauseOfInterruption.UserInterruption

if (env.BRANCH_NAME == "master") {
    properties([
        buildDiscarder(
            logRotator(
                daysToKeepStr: '90'
            )
        )
    ])
} else {
    properties([
        buildDiscarder(
            logRotator(
                numToKeepStr: '10'
            )
        )
    ])
}

def abortPreviousBuilds() {
    Run previousBuild = currentBuild.rawBuild.getPreviousBuildInProgress()

    while (previousBuild != null) {
        if (previousBuild.isInProgress()) {
            def executor = previousBuild.getExecutor()
            if (executor != null) {
                echo ">> Aborting older build #${previousBuild.number}"
                executor.interrupt(Result.ABORTED, new UserInterruption(
                    "Aborted by newer build #${currentBuild.number}"
                ))
            }
        }

        previousBuild = previousBuild.getPreviousBuildInProgress()
    }
}

abortPreviousBuilds()

try {
    node {
        checkout scm
        docker.image('pegasyseng/pantheon-build:0.0.7-jdk11').inside {
            try {
                stage('Build') {
                    sh './gradlew --no-daemon --parallel build -x :eth-reference-tests:test'
                }
                stage('Test') {
                    sh './gradlew --no-daemon --parallel test -x :eth-reference-tests:test'
                    // Disable Artemis Runtime Tests During Upgrade
                    // sh './artemis/src/main/resources/artemisTestScript.sh'
                }
                stage('Build Docker Image') {
                    sh './gradlew --no-daemon --parallel distDocker distDockerWhiteblock'
                }
                if (env.BRANCH_NAME == "master") {
                    stage('Push Docker Image') {
                        def gradleProperties = readProperties file: 'gradle.properties'
                        version = gradleProperties.version
                        def imageRepos = 'pegasyseng'
                        def image = "${imageRepos}/artemis:${version}"
                        docker.withRegistry('https://registry.hub.docker.com', 'dockerhub-pegasysengci') {
                            docker.image(image).push()
                        }
                    }

                    stage('Push WhiteBlock Docker Image') {
                        docker.withRegistry('https://registry.hub.docker.com', 'dockerhub-pegasysengci') {
                            docker.image("pegasyseng/artemis:whiteblock").push()
                        }
                    }

                    stage('Publish to Bintray') {
                      withCredentials([
                        usernamePassword(
                          credentialsId: 'pegasys-bintray',
                          usernameVariable: 'BINTRAY_USER',
                          passwordVariable: 'BINTRAY_KEY'
                        )
                      ]) {
                        sh './gradlew --no-daemon --parallel bintrayUpload'
                      }
                    }
                }
            } finally {
                archiveArtifacts '**/build/reports/**'
                archiveArtifacts '**/build/test-results/**'

                junit allowEmptyResults: true, testResults: '**/build/test-results/**/*.xml'
            }
        }
    }
} catch (ignored) {
    currentBuild.result = 'FAILURE'
} finally {
    // If we're on master and it failed, notify slack
    if (env.BRANCH_NAME == "master") {
        def currentResult = currentBuild.result ?: 'SUCCESS'
        def channel = '#team-pegasys-rd-bc'
        if (currentResult == 'SUCCESS') {
            def previousResult = currentBuild.previousBuild?.result
            if (previousResult != null && (previousResult == 'FAILURE' || previousResult == 'UNSTABLE')) {
                slackSend(
                    color: 'good',
                    message: "Beaconchain branch ${env.BRANCH_NAME} build is back to HEALTHY.\nBuild Number: #${env.BUILD_NUMBER}\n${env.BUILD_URL}",
                    channel: channel
                )
            }
        } else if (currentBuild.result == 'FAILURE') {
            slackSend(
                color: 'danger',
                message: "Beaconchain branch ${env.BRANCH_NAME} build is FAILING.\nBuild Number: #${env.BUILD_NUMBER}\n${env.BUILD_URL}",
                channel: channel
            )
        } else if (currentBuild.result == 'UNSTABLE') {
            slackSend(
                color: 'warning',
                message: "Beaconchain branch ${env.BRANCH_NAME} build is UNSTABLE.\nBuild Number: #${env.BUILD_NUMBER}\n${env.BUILD_URL}",
                channel: channel
            )
        }
    }
}

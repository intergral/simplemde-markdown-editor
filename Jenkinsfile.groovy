@Library('jenkins-tools')
import com.intergral.jenkins.Utils

def serviceName = 'simplemde-ui'

pipeline {
 agent {
  label 'docker-build'
 }
 options {
  disableConcurrentBuilds()
  buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '30'))
 }
 triggers {
  pollSCM('H/3 * * * *')
 }
 stages {
  stage('Build and Deploy (NPM)') {
   agent {
    docker {
     image 'node:8-slim'
     args '--user=root'
    }
   }
   steps {
      sh 'npm run gulp'
      sh 'npm version 1.1.$BUILD_NUMBER --no-git-tag-version'
      withCredentials([string(credentialsId: 'NPM_AUTH_TOKEN', variable: 'npm_auth')]) {
       sh 'echo "//registry.npmjs.org/:_authToken=$npm_auth" > ~/.npmrc'
      }
      sh 'cp package.json dist'
      sh 'cd dist && npm publish --tag latest'
   }
  }
 }
 post {
  failure {
   script {
    def changes = ""
    def logs = []
    def changeSets = currentBuild.changeSets
    for (hudson.scm.SubversionChangeLogSet change: changeSets) {
     logs += hudson.scm.SubversionChangeLogSet.removeDuplicatedEntries(change.getLogs())
    }
    def logSet = logs.toSet()

    for (def log: logSet) {
     changes += "\n      *${log.getAuthor()}* changed (${log.getRevision()}) ${log.getMsg()}"
     def paths = log.getAffectedPaths()
     for (def path: paths) {
      changes += "\n       <http://scm.bbn.intergral.com/viewvc/${path}?pathrev=${log.getRevision()}|${path}>"
     }
    }

    wrap([$class: 'BuildUser']) {
     slackSend botUser: true, channel: '#co-jenkins', color: 'danger', failOnError: true, message: "<$JOB_URL|$JOB_NAME> <$BUILD_URL|#$currentBuild.number> has failed after $currentBuild.durationString. \n*Started By:* $env.BUILD_USER \n*Changes:* $changes", tokenCredentialId: 'CDMS_JENKINS_SLACK_KEY'
    }
   }
  }
  unstable {
   script {
    def changes = ""
    def logs = []
    def changeSets = currentBuild.changeSets
    for (hudson.scm.SubversionChangeLogSet change: changeSets) {
     logs += hudson.scm.SubversionChangeLogSet.removeDuplicatedEntries(change.getLogs())
    }
    def logSet = logs.toSet()

    for (def log: logSet) {
     changes += "\n      *${log.getAuthor()}* changed (${log.getRevision()}) ${log.getMsg()}"
     def paths = log.getAffectedPaths()
     for (def path: paths) {
      changes += "\n       <http://scm.bbn.intergral.com/viewvc/${path}?pathrev=${log.getRevision()}|${path}>"
     }
    }

    wrap([$class: 'BuildUser']) {
     slackSend botUser: true,
     channel: '#co-jenkins',
     color: 'danger',
     failOnError: true,
     message: "<$JOB_URL|$JOB_NAME> <$BUILD_URL|#$currentBuild.number> is  after $currentBuild.durationString. \n*Started By:* $env.BUILD_USER \n*Changes:* $changes", tokenCredentialId: 'CDMS_JENKINS_SLACK_KEY'
    }
   }
  }
 }
}
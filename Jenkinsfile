pipeline {
  agent any
  environment {
    NEXUS_URL = 'http://EC2_PUBLIC_DNS:8081'
    NEXUS_REPO = 'maven-releases'
    APP_NAME = 'NumberGuessGame'
    TOMCAT_URL = 'http://EC2_PUBLIC_DNS:8082'
  }
  options { timestamps(); ansiColor('xterm'); buildDiscarder(logRotator(numToKeepStr: '20')) }
  stages {
    stage('Checkout') { steps { checkout scm } }
    stage('Build & Unit Tests') {
      steps {
        sh "mvn -B -DskipTests=false clean verify"
      }
      post { always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml' } }
    }
    stage('Static Analysis (SonarQube)') {
      steps { withSonarQubeEnv('SonarQube') { sh 'mvn -B sonar:sonar' } }
    }
    stage('Quality Gate') {
      steps { timeout(time: 10, unit: 'MINUTES') { waitForQualityGate abortPipeline: true } }
    }
    stage('Package WAR') {
      steps { sh 'mvn -B -DskipTests package && ls -l target/*.war' }
      post { success { archiveArtifacts artifacts: 'target/*.war', fingerprint: true } }
    }
    stage('Publish to Nexus') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'nexus-creds', passwordVariable: 'NEXUS_PASS', usernameVariable: 'NEXUS_USER')]) {
          sh '''
            WAR=$(ls target/*.war | head -n1)
            VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
            ARTIFACT_ID=$(mvn help:evaluate -Dexpression=project.artifactId -q -DforceStdout)
            GROUP_ID=$(mvn help:evaluate -Dexpression=project.groupId -q -DforceStdout | tr '.' '/')
            curl -u "$NEXUS_USER:$NEXUS_PASS" --upload-file "$WAR" \
              "$NEXUS_URL/repository/$NEXUS_REPO/$GROUP_ID/$ARTIFACT_ID/$VERSION/$ARTIFACT_ID-$VERSION.war"
          '''
        }
      }
    }
    stage('Deploy to Tomcat') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'tomcat-creds', passwordVariable: 'TC_PASS', usernameVariable: 'TC_USER')]) {
          sh '''
            WAR=$(ls target/*.war | head -n1)
            APP_NAME="NumberGuessGame"
            curl -s -u "$TC_USER:$TC_PASS" "$TOMCAT_URL/manager/text/undeploy?path=/$APP_NAME" || true
            curl -s -u "$TC_USER:$TC_PASS" -T "$WAR" "$TOMCAT_URL/manager/text/deploy?path=/$APP_NAME&update=true"
          '''
        }
      }
    }
  }
  post {
    success { echo 'Deployment successful!' }
    failure { echo 'Build failed. Check logs.' }
  }
}

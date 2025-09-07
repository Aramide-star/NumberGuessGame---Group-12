pipeline {
  agent any
  tools { maven 'Maven_3' } // remove if mvn is already on PATH
  environment {
    // ---- SonarQube ----
    SONARQUBE_SERVER = 'MySonarQubeServer' // must match Manage Jenkins → System

    // ---- Nexus 2 (note the /nexus path) ----
    NEXUS2_BASE   = 'http://54.234.93.25:8081/nexus'
    NEXUS2_STATUS = "${NEXUS2_BASE}/service/local/status"
    NEXUS2_UPLOAD = "${NEXUS2_BASE}/service/local/artifact/maven/content"
    NEXUS2_REPO   = 'releases' // ensure this repo exists in Nexus 2

    // ---- Tomcat over SSH (Option A) ----
    TOMCAT_SSH_CRED_ID = 'tomcat-ssh'        // Jenkins credential *ID* (SSH Username with private key)
    TOMCAT_SSH_HOST    = '54.227.58.41'      // your Tomcat server IP/host
    TOMCAT_SSH_PORT    = '22'                // change if non-standard
    TOMCAT_WEBAPPS     = '/opt/tomcat/webapps'
    TOMCAT_SERVICE_CANDIDATES = 'tomcat9 tomcat' // checked in order
    APP_NAME = 'NumberGuessingGame'
  }

  stages {
    stage('Checkout') {
      steps {
        git branch: 'dev', url: 'https://github.com/Aramide-star/NumberGuessGame---Group-12.git'
      }
    }

    stage('Build & Test') {
      steps {
        sh 'mvn -B clean verify'
      }
      post {
        always { junit 'target/surefire-reports/*.xml' }
      }
    }

    stage('SonarQube Analysis') {
      steps {
        withSonarQubeEnv("${env.SONARQUBE_SERVER}") {
          // Let the Jenkins Sonar config inject URL & token; no need to hardcode sonar.host.url
          sh '''
            set -e
            mvn -B sonar:sonar \
              -Dsonar.projectKey=com.studentapp:NumberGuessingGame \
              -Dsonar.projectName=NumberGuessingGame \
              -Dsonar.sources=src/main/java,src/main/webapp \
              -Dsonar.tests=src/test/java \
              -Dsonar.java.binaries=target/classes
          '''
        }
      }
    }

    stage('Quality Gate') {
      steps {
        timeout(time: 5, unit: 'MINUTES') {
          waitForQualityGate abortPipeline: true
        }
      }
    }

    stage('Set Unique Version') {
      steps {
        script {
          def newVer = sh(returnStdout: true, script: "date +1.0.%Y%m%d%H%M%S").trim()
          sh "mvn -B versions:set -DnewVersion=${newVer} -DgenerateBackupPoms=false"
          echo "New project version set to: ${newVer}"
        }
      }
    }

    stage('Package WAR') {
      steps {
        sh '''
          set -e
          mvn -B -DskipTests package
          ls -l target/*.war
        '''
        archiveArtifacts artifacts: 'target/*.war', fingerprint: true
      }
    }

    stage('Publish to Nexus 2 (multipart form)') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'nexus2-deploy',
                         usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
          sh '''
            set -euo pipefail

            # Probe Nexus 2
            curl -sSf "${NEXUS2_STATUS}" >/dev/null

            # Confirm repo exists
            curl -sf "${NEXUS2_BASE}/service/local/repositories" | grep -q "<id>${NEXUS2_REPO}</id>"

            # Resolve GAV & WAR
            GID="$(mvn -q -Dexpression=project.groupId -DforceStdout help:evaluate)"
            AID="$(mvn -q -Dexpression=project.artifactId -DforceStdout help:evaluate)"
            VER="$(mvn -q -Dexpression=project.version -DforceStdout help:evaluate)"
            WAR="$(ls -1 target/*.war | tail -n1)"

            echo "==> Uploading ${WAR} as ${GID}:${AID}:${VER} to ${NEXUS2_REPO}"
            CODE=$(curl -s -o /tmp/nx2_resp.txt -w '%{http_code}' \
              -u "$NEXUS_USER:$NEXUS_PASS" \
              -X POST "${NEXUS2_UPLOAD}" \
              -F "r=${NEXUS2_REPO}" \
              -F "g=${GID}" \
              -F "a=${AID}" \
              -F "v=${VER}" \
              -F "p=war" \
              -F "e=war" \
              -F "file=@${WAR}")
            if [ "$CODE" != "201" ] && [ "$CODE" != "200" ]; then
              echo "Nexus 2 upload failed: HTTP $CODE"
              head -n 200 /tmp/nx2_resp.txt || true
              exit 1
            fi
            echo "==> Upload OK (HTTP ${CODE})."
          '''
        }
      }
    }

    stage('Deploy to Tomcat via SSH') {
     steps {
    sshagent(credentials: ['tomcat-ssh']) {
      sh '''
        set -euo pipefail

        WAR="$(ls -1 target/NumberGuessingGame-*.war | tail -n1)"
        REMOTE_USER="ec2-user"
        REMOTE_HOST="54.227.58.41"     # or use the DNS name, but not both
        REMOTE_PORT="22"
        REMOTE_TMP="/tmp/$(basename "$WAR")"

        echo "==> Copying $WAR to $REMOTE_USER@$REMOTE_HOST:$REMOTE_TMP"
        scp -P "$REMOTE_PORT" -o StrictHostKeyChecking=no "$WAR" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_TMP"

        echo "==> Deploying on Tomcat"
        ssh -p "$REMOTE_PORT" -o StrictHostKeyChecking=no "$REMOTE_USER@$REMOTE_HOST" bash -lc "
          set -euo pipefail
          TOMCAT_HOME=/opt/tomcat
          sudo systemctl stop tomcat || true
          sudo rm -f \\$TOMCAT_HOME/webapps/NumberGuessingGame.war
          sudo rm -rf \\$TOMCAT_HOME/webapps/NumberGuessingGame
          sudo mv '$REMOTE_TMP' \\$TOMCAT_HOME/webapps/NumberGuessingGame.war
          sudo chown tomcat:tomcat \\$TOMCAT_HOME/webapps/NumberGuessingGame.war
          sudo systemctl start tomcat
          sleep 5
          sudo systemctl is-active --quiet tomcat
        "

        echo "==> Deployed successfully."
      '''
    }
  }
}

  post {
    failure { echo ':x: Pipeline failed. See the failing stage for details.' }
    success { echo ':white_check_mark: Pipeline completed successfully.' }
    }
  }
} 

pipeline {
  agent any
  tools { maven 'Maven3' } // remove if mvn is already on PATH

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
        // Requires: "SSH Agent" plugin OR use sshUserPrivateKey as below
        withCredentials([sshUserPrivateKey(credentialsId: "${env.TOMCAT_SSH_CRED_ID}",
                         keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
          sh '''
            set -euo pipefail

            WAR="$(ls -1 target/*.war | tail -n1)"
            [ -f "$WAR" ] || { echo "WAR not found"; exit 2; }

            REMOTE="${SSH_USER}@${TOMCAT_SSH_HOST}"
            PORT="${TOMCAT_SSH_PORT}"
            REMOTE_TMP="/tmp/${APP_NAME}.$$.war"

            echo "==> Copying WAR to ${REMOTE}:${REMOTE_TMP}"
            scp -P "$PORT" -i "$SSH_KEY" -o StrictHostKeyChecking=no "$WAR" "${REMOTE}:${REMOTE_TMP}"

            echo "==> Moving WAR into ${TOMCAT_WEBAPPS} with sudo and setting ownership"
            ssh -p "$PORT" -i "$SSH_KEY" -o StrictHostKeyChecking=no "${REMOTE}" "\
              sudo mkdir -p '${TOMCAT_WEBAPPS}' && \
              sudo mv -f '${REMOTE_TMP}' '${TOMCAT_WEBAPPS}/${APP_NAME}.war' && \
              if id tomcat >/dev/null 2>&1; then sudo chown tomcat:tomcat '${TOMCAT_WEBAPPS}/${APP_NAME}.war' || true; fi"

            echo "==> Restarting Tomcat (best-effort)"
            ssh -p "$PORT" -i "$SSH_KEY" -o StrictHostKeyChecking=no "${REMOTE}" '\
              for SVC in '"${TOMCAT_SERVICE_CANDIDATES}"'; do
                if systemctl is-enabled "$SVC" >/dev/null 2>&1; then
                  sudo systemctl restart "$SVC" && exit 0
                fi
              done
              if [ -x /opt/tomcat/bin/catalina.sh ]; then
                sudo /opt/tomcat/bin/catalina.sh stop || true
                sleep 2
                sudo /opt/tomcat/bin/catalina.sh start && exit 0
              fi
              echo "WARN: No Tomcat service/catalina.sh found to restart." >&2
            '
          '''
        }
      }
    }
  }

  post {
    failure { echo ':x: Pipeline failed. See the failing stage for details.' }
    success { echo ':white_check_mark: Pipeline completed successfully.' }
  }
}

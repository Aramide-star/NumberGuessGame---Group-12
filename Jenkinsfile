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

    // ---- Tomcat over SSH ----
    TOMCAT_SSH_CRED_ID = 'TomcatCred'        // Jenkins credential ID (SSH Username with private key)
    TOMCAT_SSH_HOST    = '54.227.58.41'
    TOMCAT_SSH_PORT    = '22'
    TOMCAT_WEBAPPS     = '/opt/tomcat/webapps'
    APP_NAME           = 'NumberGuessingGame'
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
        always {
          junit 'target/surefire-reports/*.xml'
        }
      }
    }

    stage('SonarQube Analysis') {
      steps {
        withSonarQubeEnv("${env.SONARQUBE_SERVER}") {
          sh '''
            mvn -B sonar:sonar \
              -Dsonar.projectKey=com.studentapp:NumberGuessingGame \
              -Dsonar.projectName=NumberGuessingGame \
              -Dsonar.host.url=http://54.234.39.41:9000/ \
              -Dsonar.sources=src/main/java,src/main/webapp \
              -Dsonar.tests=src/test/java \
              -Dsonar.java.binaries=target/classes
          '''
        }
      }
    }

    stage('Quality Gate') {
      steps {
        timeout(time: 20, unit: 'MINUTES') {
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
        sh 'mvn -B -DskipTests package'
        archiveArtifacts artifacts: 'target/*.war', fingerprint: true
      }
    }

    stage('Publish to Nexus 2') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'nexus2-deploy',
                          usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
          sh """
            set -euo pipefail

            echo "==> Checking Nexus 2 status: ${NEXUS2_STATUS}"
            STATUS=\$(curl -s -o /dev/null -w '%{http_code}' "${NEXUS2_STATUS}")
            [ "\$STATUS" = "200" ] || { echo "Nexus status HTTP \$STATUS"; exit 1; }

            echo "==> Verifying repository exists: ${NEXUS2_REPO}"
            curl -sf "${NEXUS2_BASE}/service/local/repositories" | grep -q "<id>${NEXUS2_REPO}</id>"

            echo "==> Resolving GAV & WAR"
            GID=\$(mvn -q -Dexpression=project.groupId -DforceStdout help:evaluate)
            AID=\$(mvn -q -Dexpression=project.artifactId -DforceStdout help:evaluate)
            VER=\$(mvn -q -Dexpression=project.version -DforceStdout help:evaluate)
            WAR=\$(ls -1 target/*.war | tail -n1)

            echo "Resolved: \$GID:\$AID:\$VER"
            echo "WAR: \$WAR"
            [ -s "\$WAR" ] || { echo "WAR not found"; exit 1; }

            echo "==> Uploading to Nexus 2 repo '${NEXUS2_REPO}'"
            CODE=\$(curl -s -o /tmp/nx2_resp.txt -w '%{http_code}' \\
              -u "\$NEXUS_USER:\$NEXUS_PASS" \\
              -X POST "${NEXUS2_UPLOAD}" \\
              -F r="${NEXUS2_REPO}" \\
              -F g="\$GID" \\
              -F a="\$AID" \\
              -F v="\$VER" \\
              -F p="war" \\
              -F e="war" \\
              -F file=@"\$WAR")

            if [ "\$CODE" != "201" ] && [ "\$CODE" != "200" ]; then
              echo "Nexus 2 upload failed (HTTP \$CODE)"
              head -n 200 /tmp/nx2_resp.txt || true
              exit 1
            fi
            echo "==> Upload OK (HTTP \$CODE)."
          """
        }
      }
    }

    stage('Deploy to Tomcat') {
      steps {
        // Use the existing Jenkins SSH key credential (must be the correct OpenSSH private key, no passphrase)
        sshagent(credentials: [env.TOMCAT_SSH_CRED_ID]) {
          sh '''
            set -euo pipefail

            WAR="$(ls -1 target/*.war | tail -n 1)"
            REMOTE_USER="ec2-user"
            REMOTE_HOST="${TOMCAT_SSH_HOST}"
            REMOTE_PORT="${TOMCAT_SSH_PORT}"
            REMOTE="${REMOTE_USER}@${REMOTE_HOST}"
            REMOTE_TMP="/tmp/$(basename "$WAR")"

            echo "==> Sanity-check SSH"
            ssh -p "$REMOTE_PORT" -o BatchMode=yes -o StrictHostKeyChecking=no "$REMOTE" 'echo OK' >/dev/null

            echo "==> Copying $WAR to $REMOTE:$REMOTE_TMP"
            scp -P "$REMOTE_PORT" -o StrictHostKeyChecking=no "$WAR" "$REMOTE:$REMOTE_TMP"

            echo "==> Deploying on Tomcat"
            ssh -p "$REMOTE_PORT" -o StrictHostKeyChecking=no "$REMOTE" "set -e;
              sudo systemctl stop tomcat || sudo systemctl stop tomcat9 || true;
              sudo rm -f '${TOMCAT_WEBAPPS}/${APP_NAME}.war';
              sudo rm -rf '${TOMCAT_WEBAPPS}/${APP_NAME}';
              sudo mv '${REMOTE_TMP}' '${TOMCAT_WEBAPPS}/${APP_NAME}.war';
              sudo chown tomcat:tomcat '${TOMCAT_WEBAPPS}/${APP_NAME}.war';
              sudo systemctl start tomcat || sudo systemctl start tomcat9;
              sleep 5;
              (sudo systemctl is-active --quiet tomcat || sudo systemctl is-active --quiet tomcat9)
            "

            echo "==> Deployed successfully."
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

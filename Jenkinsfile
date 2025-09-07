pipeline {
  agent any
  tools { maven 'Maven_3' } // remove if mvn is already on PATH

  environment {
    // SonarQube (matches Manage Jenkins → System)
    SONARQUBE_SERVER = 'MySonarQubeServer'

    // Nexus 2 (note the /nexus path)
    NEXUS2_BASE   = 'http://54.234.93.25:8081/nexus'
    NEXUS2_STATUS = "${NEXUS2_BASE}/service/local/status"
    NEXUS2_UPLOAD = "${NEXUS2_BASE}/service/local/artifact/maven/content"
    NEXUS2_REPO   = 'releases'   // make sure this repo exists in Nexus 2

    // App + Tomcat
    APP_NAME   = 'NumberGuessingGame'
    TOMCAT_HOST = '54.227.58.41'
    REMOTE_DIR  = '/opt/tomcat/webapps'
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

    // ---------- FIXED STAGE ----------
    stage('Publish to Nexus 2') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'nexus2-deploy',
          usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
          sh '''
            set -euo pipefail

            echo "==> Checking Nexus 2 status: ${NEXUS2_STATUS}"
            STATUS=$(curl -s -o /dev/null -w '%{http_code}' "${NEXUS2_STATUS}")
            if [ "$STATUS" != "200" ]; then
              echo "ERROR: Nexus status endpoint returned HTTP $STATUS"
              exit 1
            fi

            echo "==> Verifying repository exists: ${NEXUS2_REPO}"
            curl -sf "${NEXUS2_BASE}/service/local/repositories" -o /tmp/repos.xml
            if ! grep -q "<id>${NEXUS2_REPO}</id>" /tmp/repos.xml; then
              echo "ERROR: Repository '${NEXUS2_REPO}' not found. Available repos are:"
              grep -o '<id>[^<]*</id>' /tmp/repos.xml | sed 's/<[^>]*>//g' || true
              exit 1
            fi

            echo "==> Resolving GAV & WAR"
            GID="$(mvn -q -Dexpression=project.groupId -DforceStdout help:evaluate)"
            AID="$(mvn -q -Dexpression=project.artifactId -DforceStdout help:evaluate)"
            VER="$(mvn -q -Dexpression=project.version -DforceStdout help:evaluate)"
            WAR="$(ls -1 target/*.war | tail -n 1)"
            [ -s "$WAR" ] || { echo "ERROR: WAR not found"; exit 1; }

            echo "Resolved: ${GID}:${AID}:${VER}"
            echo "WAR: ${WAR}"

            echo "==> Uploading to Nexus 2 repo '${NEXUS2_REPO}'"
            CODE=$(curl -s -o /tmp/nx2_resp.txt -w '%{http_code}' \
              -u "${NEXUS_USER}:${NEXUS_PASS}" \
              -X POST "${NEXUS2_UPLOAD}" \
              -F "r=${NEXUS2_REPO}" \
              -F "g=${GID}" \
              -F "a=${AID}" \
              -F "v=${VER}" \
              -F "p=war" \
              -F "e=war" \
              -F "file=@${WAR}")

            if [ "$CODE" != "201" ] && [ "$CODE" != "200" ]; then
              echo "ERROR: Nexus 2 upload failed: HTTP $CODE"
              head -n 200 /tmp/nx2_resp.txt || true
              exit 1
            fi
            echo "==> Upload OK (HTTP ${CODE})."
          '''
        }
      }
    }

    stage('Deploy to Tomcat') {
  steps {
    // Use the SSH credential ID that exists in Jenkins
    sshagent(credentials: ['TomcatCred']) {
      sh '''
        set -euo pipefail

        WAR="$(ls -1 target/*.war | tail -n 1)"
        REMOTE_USER="ec2-user"
        REMOTE_HOST="${TOMCAT_HOST}"             # from environment block
        REMOTE="${REMOTE_USER}@${REMOTE_HOST}"
        REMOTE_PATH="${REMOTE_DIR}/${APP_NAME}.war"

        echo "==> Sanity-check SSH"
        ssh -o BatchMode=yes -o StrictHostKeyChecking=no "$REMOTE" 'echo OK' >/dev/null

        echo "==> Copying $WAR to $REMOTE:$REMOTE_PATH"
        scp -P 22 -o StrictHostKeyChecking=no "$WAR" "$REMOTE:$REMOTE_PATH"

        echo "==> Restarting Tomcat"
        ssh -p 22 -o StrictHostKeyChecking=no "$REMOTE" '
          set -e
          if systemctl list-units --type=service | grep -q tomcat9; then
            sudo systemctl restart tomcat9
          elif systemctl list-units --type=service | grep -q tomcat; then
            sudo systemctl restart tomcat
          else
            echo "WARN: No Tomcat service found; ensure auto-deploy is enabled."
          fi
          sleep 5
          (sudo systemctl is-active --quiet tomcat9 || sudo systemctl is-active --quiet tomcat)
        '

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

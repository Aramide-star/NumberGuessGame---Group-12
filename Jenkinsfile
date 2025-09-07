pipeline {
  agent any

  environment {
    // SonarQube alias as configured in Jenkins > Manage Jenkins > System
    SONARQUBE_SERVER = 'MySonarQubeServer'

    // Nexus 2 endpoints (note the /nexus path)
    NEXUS2_BASE   = 'http://3.93.170.101:8081/nexus'
    NEXUS2_STATUS = "${NEXUS2_BASE}/service/local/status"
    NEXUS2_UPLOAD = "${NEXUS2_BASE}/service/local/artifact/maven/content"
    NEXUS2_REPO   = 'releases'   // make sure this repo exists in Nexus 2
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
          sh 'mvn -B sonar:sonar -Dsonar.projectKey=com.studentapp:NumberGuessingGame -Dsonar.projectName=NumberGuessingGame -Dsonar.host.url=http://54.221.103.235:9000/ -Dsonar.sources=src/main/java,src/main/webapp -Dsonar.tests=src/test/java -Dsonar.java.binaries=target/classes'
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
          // Example: 1.0.YYYYMMDDHHMMSS
          def newVer = sh(returnStdout: true, script: "date +1.0.%Y%m%d%H%M%S").trim()
          sh "mvn -B versions:set -DnewVersion=${newVer} -DgenerateBackupPoms=false"
          echo "New project version set to: ${newVer}"
        }
      }
    }

    stage('Package WAR') {
      steps {
        sh 'mvn -B -DskipTests package'
        sh 'ls -l target/*.war'
        archiveArtifacts artifacts: 'target/*.war', fingerprint: true
      }
    }

    stage('Publish to Nexus 2 (multipart form)') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'nexus2-deploy', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
          script {
            // Resolve GAV and the SINGLE war path
            def gid   = sh(returnStdout: true, script: "mvn -q -Dexpression=project.groupId -DforceStdout help:evaluate").trim()
            def aid   = sh(returnStdout: true, script: "mvn -q -Dexpression=project.artifactId -DforceStdout help:evaluate").trim()
            def ver   = sh(returnStdout: true, script: "mvn -q -Dexpression=project.version -DforceStdout help:evaluate").trim()
            def war   = sh(returnStdout: true, script: "ls target/*.war | tail -n 1").trim()  // pick the newest WAR

            echo "==> Checking Nexus 2 status: ${env.NEXUS2_STATUS}"
            def scode = sh(returnStdout: true, script: "curl -s -o /dev/null -w '%{http_code}' '${env.NEXUS2_STATUS}'").trim()
            if (scode != '200') {
              error "Nexus status endpoint returned HTTP ${scode} (expected 200). Check NEXUS2_BASE and server availability."
            }

            echo "==> Verifying repository exists: ${env.NEXUS2_REPO}"
            sh "curl -sf ${env.NEXUS2_BASE}/service/local/repositories | grep -q '<id>${env.NEXUS2_REPO}</id>'"

            echo "==> Uploading ${war} as ${gid}:${aid}:${ver} to repo '${env.NEXUS2_REPO}'"
            def up = sh(
              returnStdout: true,
              script: """
                curl -s -o /tmp/nx2_resp.txt -w '%{http_code}' \\
                  -u '${NEXUS_USER}:${NEXUS_PASS}' \\
                  -X POST '${NEXUS2_UPLOAD}' \\
                  -F r='${NEXUS2_REPO}' \\
                  -F g='${gid}' \\
                  -F a='${aid}' \\
                  -F v='${ver}' \\
                  -F p='war' \\
                  -F e='war' \\
                  -F file=@'${war}'
              """
            ).trim()

            if (up == '401' || up == '403') {
              def body = sh(returnStdout: true, script: "head -n 200 /tmp/nx2_resp.txt || true")
              error """Nexus 2 upload denied (HTTP ${up}).
Hints:
 - Confirm Jenkins credentialsId 'nexus2-deploy' maps to a valid Nexus user.
 - Ensure the user has 'Create/Read/Update' on repo '${env.NEXUS2_REPO}' (Maven2 hosted).
 - Repo name must be exactly '${env.NEXUS2_REPO}'.
Response:
${body}
"""
            }
            if (up != '201' && up != '200') {
              def body = sh(returnStdout: true, script: "head -n 200 /tmp/nx2_resp.txt || true")
              error "Nexus 2 upload failed. HTTP ${up}\nResponse:\n${body}"
            }

            echo "==> Upload OK (HTTP ${up})."
          }
        }
      }
    }

    stage('Deploy to Tomcat via SSH') {
      steps {
        withCredentials([sshUserPrivateKey(credentialsId: 'tomcat-ssh', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
          script {
            def war = sh(returnStdout: true, script: "ls target/*.war | tail -n 1").trim()
            def appName   = 'NumberGuessingGame'
            def remoteDir = '/opt/tomcat/webapps'
            def host      = '3.9.244.77'   // SSH host (no port here)

            echo "==> Copying WAR to ${host}:${remoteDir}/${appName}.war"
            sh "scp -i '${SSH_KEY}' -o StrictHostKeyChecking=no '${war}' '${SSH_USER}@${host}:${remoteDir}/${appName}.war'"

            echo "==> Restarting Tomcat (best-effort: tomcat9, then tomcat)"
            sh """
              ssh -i '${SSH_KEY}' -o StrictHostKeyChecking=no '${SSH_USER}@${host}' \\
                'if systemctl list-units --type=service | grep -q tomcat9; then sudo systemctl restart tomcat9; \\
                  elif systemctl list-units --type=service | grep -q tomcat; then sudo systemctl restart tomcat; \\
                  else echo "WARN: No tomcat systemd unit found. If using catalina.sh, ensure auto-deploy is on."; fi'
            """
          }
        }
      }
    }
  }

  post {
    failure {
      echo ':x: Pipeline failed. See the failing stage for details.'
    }
    success {
      echo ':white_check_mark: Pipeline completed successfully.'
    }
  }
}

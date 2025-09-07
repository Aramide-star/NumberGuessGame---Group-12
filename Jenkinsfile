pipeline {
  agent any

  environment {
    SONARQUBE_SERVER = 'MySonarQubeServer'
    NEXUS2_BASE      = 'http://54.234.93.25:8081/nexus'
    NEXUS2_STATUS    = "${NEXUS2_BASE}/service/local/status"
    NEXUS2_UPLOAD    = "${NEXUS2_BASE}/service/local/artifact/maven/content"
    NEXUS2_REPO      = 'releases'
    APP_NAME         = 'NumberGuessingGame'
    TOMCAT_HOST      = '54.227.58.41'
    REMOTE_DIR       = '/opt/tomcat/webapps'
    
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
        withSonarQubeEnv('MySonarQubeServer') {
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
        script {
          timeout(time: 20, unit: 'MINUTES') {
            waitForQualityGate abortPipeline: true
          }
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
        withCredentials([usernamePassword(credentialsId: 'nexus2-deploy', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
          script {
            def gid = sh(returnStdout: true, script: "mvn -q -Dexpression=project.groupId -DforceStdout help:evaluate").trim()
            def aid = sh(returnStdout: true, script: "mvn -q -Dexpression=project.artifactId -DforceStdout help:evaluate").trim()
            def ver = sh(returnStdout: true, script: "mvn -q -Dexpression=project.version -DforceStdout help:evaluate").trim()
            def war = sh(returnStdout: true, script: "find target -name '*.war' | sort | tail -n 1").trim()

            echo "Checking Nexus status..."
            def scode = sh(returnStdout: true, script: "curl -s -o /dev/null -w '%{http_code}' \"$NEXUS2_STATUS\"").trim()
            if (scode != '200') {
              error "Nexus status check failed with HTTP ${scode}"
            }

            echo "Uploading WAR to Nexus..."
            def up = sh(returnStdout: true, script: '''
              curl -s -o /tmp/nx2_resp.txt -w '%{http_code}' \
                -u "$NEXUS_USER:$NEXUS_PASS" \
                -X POST "$NEXUS2_UPLOAD" \
                -F r="$NEXUS2_REPO" \
                -F g="$gid" \
                -F a="$aid" \
                -F v="$ver" \
                -F p="war" \
                -F e="war" \
                -F file=@"$war"
            ''').trim()

            if (up != '201' && up != '200') {
              def body = sh(returnStdout: true, script: "head -n 100 /tmp/nx2_resp.txt || true")
              error "Nexus upload failed (HTTP ${up})\nResponse:\n${body}"
            }

            echo "Upload successful (HTTP ${up})"
          }
        }
      }
    }

    stage('Deploy to Tomcat') {
      steps {
        withCredentials([sshUserPrivateKey(credentialsId: 'tomcat-ssh', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
          script {
            def war = sh(returnStdout: true, script: "find target -name '*.war' | sort | tail -n 1").trim()

            echo "Deploying WAR to Tomcat at ${TOMCAT_HOST}"

            sh '''
              eval $(ssh-agent -s)
              ssh-add "$SSH_KEY"
              scp -o StrictHostKeyChecking=no "$war" "$SSH_USER@$TOMCAT_HOST:$REMOTE_DIR/$APP_NAME.war"
            '''

            sh '''
              ssh -o StrictHostKeyChecking=no "$SSH_USER@$TOMCAT_HOST" '
                if systemctl list-units --type=service | grep -q tomcat9; then
                  sudo systemctl restart tomcat9;
                elif systemctl list-units --type=service | grep -q tomcat; then
                  sudo systemctl restart tomcat;
                else
                  echo "WARN: No Tomcat service found. Manual restart may be required.";
                fi
              '
            '''
          }
        }
      }
    }
  }

  post {
    success {
      echo ':white_check_mark: Pipeline completed successfully.'
    }
    failure {
      echo ':x: Pipeline failed. Check logs for details.'
    }
  }
}

pipeline {
    agent any
    tools { jdk 'jdk17'; maven 'maven3' }
    options { timestamps(); timeout(time: 20, unit: 'MINUTES') }
    stages {
        stage('Verify') { steps { sh 'mvn -B -ntp verify' } }
    }
    post {
        always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml,target/failsafe-reports/*.xml' }
        success { archiveArtifacts artifacts: 'target/operation-ledger-*.jar', fingerprint: true }
    }
}

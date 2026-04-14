pipeline {
    agent {
        docker {
            image "cart.lge.com/swte/jenkins-dev:latest"
        }
    }
    options {
        disableConcurrentBuilds(abortPrevious: true)
    }
    stages {
        stage("Build & Verify") {
            steps {
                sh "HOME=${env.WORKSPACE}/home mvn clean verify -Pstatic-analysis"
            }
        }
    }
    post {
        always {
            recordIssues enabledForFailure: true, tools: [mavenConsole(), java(), javaDoc()]
            recordIssues enabledForFailure: true, tool: checkStyle()
            recordIssues enabledForFailure: true, tool: spotBugs()
            recordIssues enabledForFailure: true, tool: cpd(pattern: '**/target/cpd.xml')
            recordIssues enabledForFailure: true, tool: pmdParser(pattern: '**/target/pmd.xml')
            junit "**/target/surefire-reports/*.xml"
            recordCoverage tools: [[parser: 'JACOCO', pattern: '**/target/site/jacoco/jacoco.xml']]
            cleanWs disableDeferredWipeout: true
        }
    }
}

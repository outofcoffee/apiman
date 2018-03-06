pipeline {
  agent {
    node {
      label 'ci'
      customWorkspace 'workspace/apiman'
    }
  }

  options {
      timeout(time: 1, unit: 'HOURS')
      retry(1)
  }

  parameters {
    string(name: 'DISTRO_VERSION', defaultValue: 'dev', description: 'The version of the distribution, e.g. 1.2.9.DysonP12')
  }

  stages {
    stage('Build and Test') {
      steps {
        sh 'mvn clean install'
      }
    }
    stage('Publish') {
      steps {
        script {
            if ('dev' == params.DISTRO_VERSION) {
                println 'Skipping publish of development version'
            } else {
                sh "aws s3 cp ./distro/tomcat8/target/apiman-distro-tomcat8-${params.DISTRO_VERSION}-overlay.zip s3://cyclones-maven-repository/thirdparty/io/apiman/apiman-distro-tomcat8/${params.DISTRO_VERSION}/apiman-distro-tomcat8-${params.DISTRO_VERSION}-overlay.zip"
            }
        }
      }
    }
  }

  post {
      always {
        junit '**/surefire-reports/*.xml'
    }
  }
}

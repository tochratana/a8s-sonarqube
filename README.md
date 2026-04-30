# a8s-sonarqube

Jenkins Shared Library for running SonarQube scans from A8S pipelines.

## Jenkins setup

1. Push this folder to a Git repository, for example `a8s-sonarqube`.
2. In Jenkins, open **Manage Jenkins > System > Global Pipeline Libraries**.
3. Add a library:
   - Name: `a8s-sonarqube`
   - Default version: `main`
   - Retrieval method: Git
   - Repository URL: your `a8s-sonarqube` Git URL
4. Configure SonarQube in Jenkins with server name `sonarqube`.
5. Install `sonar-scanner` on the Jenkins agent or create a Jenkins tool installation and pass its name through `SONARQUBE_SCANNER_TOOL`.

## Backend setup

Set these environment variables for `a8s-backend`:

```bash
SONARQUBE_BASE_URL=https://your-sonarqube-domain.com
SONARQUBE_TOKEN=your_backend_read_token
SONARQUBE_JENKINS_SERVER_NAME=sonarqube
SONARQUBE_SCANNER_TOOL=sonar-scanner
SONARQUBE_SCAN_ENABLED=true
SONARQUBE_QUALITY_GATE_ENABLED=false
```

The backend exposes project scan data at:

```text
GET /api/v1/projects/{projectId}/sonarqube
```

The returned `projectKey` matches the Jenkins pipeline format:

```text
<workspaceNamespace>-<projectName>
```

## Pipeline usage

```groovy
@Library(['share_lib@master', 'a8s-sonarqube@main']) _

stage('SonarQube Analysis') {
    steps {
        dir('user-app') {
            a8sSonarScan(
                server: 'sonarqube',
                projectKey: 'my-project',
                projectName: 'my-project',
                sources: '.'
            )
        }
    }
}

stage('SonarQube Quality Gate') {
    steps {
        a8sSonarQualityGate(timeoutMinutes: 5, abortPipeline: true)
    }
}
```

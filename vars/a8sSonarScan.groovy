def call(Map config = [:]) {
    String server = trimToDefault(config.server, 'sonarqube')
    String projectKey = required(config.projectKey, 'projectKey')
    String projectName = trimToDefault(config.projectName, projectKey)
    String sources = trimToDefault(config.sources, '.')
    String sourceEncoding = trimToDefault(config.sourceEncoding, 'UTF-8')
    String scanner = resolveScannerCommand(config)
    String javaBinaries = trimToNull(config.javaBinaries) ?: resolveJavaBinaries(config)

    Map sonarProperties = [
        'sonar.projectKey': projectKey,
        'sonar.projectName': projectName,
        'sonar.sources': sources,
        'sonar.sourceEncoding': sourceEncoding
    ]

    putIfPresent(sonarProperties, 'sonar.projectVersion', config.projectVersion)
    putIfPresent(sonarProperties, 'sonar.exclusions', config.exclusions)
    putIfPresent(sonarProperties, 'sonar.coverage.exclusions', config.coverageExclusions)
    putIfPresent(sonarProperties, 'sonar.tests', config.tests)
    putIfPresent(sonarProperties, 'sonar.test.inclusions', config.testInclusions)
    putIfPresent(sonarProperties, 'sonar.javascript.lcov.reportPaths', config.javascriptLcovReportPaths)
    putIfPresent(sonarProperties, 'sonar.typescript.lcov.reportPaths', config.typescriptLcovReportPaths)
    putIfPresent(sonarProperties, 'sonar.python.coverage.reportPaths', config.pythonCoverageReportPaths)
    putIfPresent(sonarProperties, 'sonar.coverage.jacoco.xmlReportPaths', config.jacocoXmlReportPaths)
    putIfPresent(sonarProperties, 'sonar.java.binaries', javaBinaries)

    Map extraProperties = config.properties instanceof Map ? config.properties : [:]
    extraProperties.each { key, value ->
        if (key != null && value != null && value.toString().trim()) {
            sonarProperties[key.toString()] = value.toString().trim()
        }
    }

    String args = sonarProperties
        .collect { key, value -> shellQuote("-D${key}=${value}") }
        .join(' \\\n  ')

    withSonarQubeEnv(server) {
        sh label: 'SonarQube analysis', script: """#!/usr/bin/env bash
          set -euo pipefail
          ${scanner} \\
            ${args}
          """
    }
}

private String resolveScannerCommand(Map config) {
    String scannerCommand = trimToNull(config.scannerCommand)
    if (scannerCommand) {
        return scannerCommand
    }

    String scannerTool = trimToNull(config.scannerTool)
    if (scannerTool) {
        String scannerHome = tool(scannerTool)
        return shellQuote("${scannerHome}/bin/sonar-scanner")
    }

    return 'sonar-scanner'
}

private String resolveJavaBinaries(Map config) {
    if (!isEnabled(config.autoJavaBinaries, true) || !containsJavaSources()) {
        return null
    }

    String existingBinaries = findJavaBinaries()
    if (existingBinaries) {
        return existingBinaries
    }

    if (!isEnabled(config.autoCompileJava, true)) {
        return null
    }

    compileJavaClasses()

    String compiledBinaries = findJavaBinaries()
    if (!compiledBinaries) {
        error('Java sources were found, but no compiled class directories were found for SonarQube.')
    }

    return compiledBinaries
}

private boolean containsJavaSources() {
    return sh(
        script: '''
            find . -type f -name '*.java' \
              ! -path './build/*' \
              ! -path './target/*' \
              ! -path './.git/*' \
              ! -path './node_modules/*' \
              -print -quit | grep -q .
        ''',
        returnStatus: true
    ) == 0
}

private String findJavaBinaries() {
    return sh(
        script: '''
            find . -type d \\( \
              -path '*/build/classes/java/main' \
              -o -path '*/target/classes' \
            \\) | sort | paste -sd, -
        ''',
        returnStdout: true
    ).trim()
}

private void compileJavaClasses() {
    sh label: 'Compile Java classes for SonarQube', script: '''
        set -e
        if [ -f ./gradlew ]; then
            chmod +x ./gradlew
            ./gradlew classes -x test --no-daemon
        elif [ -f build.gradle ] || [ -f build.gradle.kts ]; then
            if command -v gradle >/dev/null 2>&1; then
                gradle classes -x test --no-daemon
            else
                echo "Gradle project detected but neither ./gradlew nor gradle is available." >&2
                exit 1
            fi
        elif [ -f ./mvnw ]; then
            chmod +x ./mvnw
            ./mvnw -DskipTests compile
        elif [ -f pom.xml ]; then
            if command -v mvn >/dev/null 2>&1; then
                mvn -DskipTests compile
            else
                echo "Maven project detected but neither ./mvnw nor mvn is available." >&2
                exit 1
            fi
        else
            echo "Java sources were found, but no supported Maven or Gradle build file was found." >&2
            exit 1
        fi
    '''
}

private boolean isEnabled(Object value, boolean fallback) {
    String normalized = trimToNull(value)
    if (normalized == null) {
        return fallback
    }
    return !['false', '0', 'no', 'off'].contains(normalized.toLowerCase())
}

private void putIfPresent(Map target, String key, Object value) {
    String resolved = trimToNull(value)
    if (resolved) {
        target[key] = resolved
    }
}

private String required(Object value, String name) {
    String resolved = trimToNull(value)
    if (!resolved) {
        error("Missing required SonarQube option: ${name}")
    }
    return resolved
}

private String trimToDefault(Object value, String fallback) {
    String resolved = trimToNull(value)
    return resolved ?: fallback
}

private String trimToNull(Object value) {
    if (value == null) {
        return null
    }
    String resolved = value.toString().trim()
    return resolved ? resolved : null
}

private String shellQuote(String value) {
    return "'${value.replace("'", "'\"'\"'")}'"
}

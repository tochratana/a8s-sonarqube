def call(Map config = [:]) {
    String server = trimToDefault(config.server, 'sonarqube')
    String projectKey = required(config.projectKey, 'projectKey')
    String projectName = trimToDefault(config.projectName, projectKey)
    String sources = trimToDefault(config.sources, '.')
    String sourceEncoding = trimToDefault(config.sourceEncoding, 'UTF-8')
    String scanner = resolveScannerCommand(config)

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
    putIfPresent(sonarProperties, 'sonar.java.binaries', config.javaBinaries)

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

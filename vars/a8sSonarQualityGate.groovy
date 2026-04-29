def call(Map config = [:]) {
    int timeoutMinutes = parsePositiveInt(config.timeoutMinutes, 5)
    boolean abortPipeline = parseBoolean(config.abortPipeline, true)

    timeout(time: timeoutMinutes, unit: 'MINUTES') {
        waitForQualityGate abortPipeline: abortPipeline
    }
}

private int parsePositiveInt(Object value, int fallback) {
    if (value == null || !value.toString().trim()) {
        return fallback
    }

    try {
        int parsed = value.toString().trim() as int
        return parsed > 0 ? parsed : fallback
    } catch (ignored) {
        return fallback
    }
}

private boolean parseBoolean(Object value, boolean fallback) {
    if (value == null || !value.toString().trim()) {
        return fallback
    }

    return value.toString().trim().toBoolean()
}

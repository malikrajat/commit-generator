package com.rajatmalik.commitgenerator.core.security

import com.rajatmalik.commitgenerator.core.domain.Sensitivity

private data class SensitiveRule(
    val pattern: Regex,
    val sensitivity: Sensitivity,
    val label: String,
)

private val RULES: List<SensitiveRule> = listOf(
    SensitiveRule(Regex("(^|/)\\.env(\\.|$)", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "environment file"),
    SensitiveRule(Regex("(^|/)\\.envrc$", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "direnv environment file"),
    SensitiveRule(Regex("\\.(pem|key|p12|pfx|keystore|jks|asc|gpg|kdbx)$", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "key material"),
    SensitiveRule(Regex("(^|/)id_(rsa|dsa|ecdsa|ed25519)(\\.|$)", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "ssh private key"),
    SensitiveRule(Regex("(^|/)\\.npmrc$", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "npm credentials"),
    SensitiveRule(Regex("(^|/)\\.pypirc$", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "pypi credentials"),
    SensitiveRule(Regex("(^|/)\\.netrc$", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "netrc credentials"),
    SensitiveRule(Regex("(^|/)\\.git-credentials$", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "git credentials"),
    SensitiveRule(Regex("(^|/)\\.ssh/", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "ssh directory"),
    SensitiveRule(Regex("(^|/)\\.aws/", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "aws directory"),
    SensitiveRule(Regex("(^|/)\\.azure/", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "azure directory"),
    SensitiveRule(Regex("(^|/)\\.docker/config\\.json$", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "docker credentials"),
    SensitiveRule(Regex("(^|/)secrets?/", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "secrets directory"),
    SensitiveRule(Regex("(^|/)credentials?(\\.|$)", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "credentials file"),
    SensitiveRule(Regex("service[-_]?account[^/]*\\.json$", RegexOption.IGNORE_CASE), Sensitivity.BLOCKED, "service account key"),
    SensitiveRule(Regex("(^|/)kubeconfig$", RegexOption.IGNORE_CASE), Sensitivity.SUSPICIOUS, "kubeconfig"),
    SensitiveRule(Regex("\\.tfvars$", RegexOption.IGNORE_CASE), Sensitivity.SUSPICIOUS, "terraform variables"),
    SensitiveRule(Regex("(^|/)config/(secrets?|credentials?)[^/]*$", RegexOption.IGNORE_CASE), Sensitivity.SUSPICIOUS, "config secrets"),
    SensitiveRule(Regex("(^|/)\\.pulumi/", RegexOption.IGNORE_CASE), Sensitivity.SUSPICIOUS, "pulumi state"),
)

data class SensitivityVerdict(
    val sensitivity: Sensitivity,
    val label: String? = null,
)

fun classifySensitivePath(path: String): SensitivityVerdict {
    for (rule in RULES) {
        if (rule.pattern.containsMatchIn(path)) {
            return SensitivityVerdict(rule.sensitivity, rule.label)
        }
    }
    return SensitivityVerdict(Sensitivity.NONE)
}

fun isBlockedPath(path: String): Boolean = classifySensitivePath(path).sensitivity == Sensitivity.BLOCKED

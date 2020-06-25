package com.epam.edp.stages.impl.cd.impl

import com.epam.edp.stages.impl.cd.Stage

@Stage(name = "security-check")
class SecurityCheck {
    Script script
    void run(context) {
        script.dir("${context.workDir}") {
            script.withCredentials([script.string(credentialsId: "${context.job.stageName}.url", variable: "serverUrl")]) {
                script.withKubeConfig([credentialsId: "${context.job.stageName}.token", serverUrl: "${script.serverUrl}"]) {
                    context.platform.runPod(context.job.deployProject, context.job.imageForSecurityCheck, context.job.securityCheckName, context.job.optionsForSecurityCheck)
                }
            }
        }
    }
}
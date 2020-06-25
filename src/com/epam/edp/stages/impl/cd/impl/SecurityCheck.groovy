package com.epam.edp.stages.impl.cd.impl

import com.epam.edp.stages.impl.cd.Stage

@Stage(name = "security-check")
class SecurityCheck {
    Script script
    void run(context) {
        def codebasesList = context.job.codebasesList
        codebasesList.each() { codebase -> 
            def options = "${context.job.optionsForSecurityCheck} ${codebase.name}"
            script.dir("${context.workDir}") {
                script.withCredentials([script.string(credentialsId: "${context.job.stageName}.url", variable: "serverUrl")]) {
                    script.withKubeConfig([credentialsId: "${context.job.stageName}.token", serverUrl: "${script.serverUrl}"]) { 
                        def output = context.platform.runPod(context.job.deployProject, context.job.imageForSecurityCheck, context.job.securityCheckName, options)
                        script.println("[JENKINS][DEBUG] ${output}")
                    }
                }
            }
            script.node("python") {
                        script.sh(
                            script: "pip install reportportal-client",
                            returnStdout: true)
                script.withCredentials([script.string(credentialsId: "rp.tims_reporter", variable: "token")]) {
                    script.sh(
                        script: "python --version",
                        returnStdout: true)
                }
            }      
        }
    }
}
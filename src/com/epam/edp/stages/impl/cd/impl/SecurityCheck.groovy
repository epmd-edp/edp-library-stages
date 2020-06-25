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
                        context.securityChecOut = context.platform.runPod(context.job.deployProject, context.job.imageForSecurityCheck, context.job.securityCheckName, options)
                    }
                }
            }
            script.node("python") {
                        script.sh(
                            script: "pip install reportportal-client",
                            returnStdout: true)
                script.withCredentials([script.string(credentialsId: "rp.tims_reporter", variable: "token")]) {
                    script.println("[JENKINS][DEBUG] ${context.securityChecOut}")
                    def result = context.securityChecOut.toString().replaceAll('\\"', '')
                    script.sh(
                        script: "echo 'from time import time\nfrom reportportal_client import ReportPortalService\ndef timestamp():\n    return str(int(time() * 1000))\nendpoint = \"https://commerce-dp.ah.nl\"\nproject = \"ahnl-tims\"\nlaunch_name = \"${codebase.name.toUpperCase()} SECURITY TESTS\"\nlaunch_doc = \"Security tests for ${codebase.name} service\"\nattributes = [\"security\"]\nservice = ReportPortalService(endpoint=endpoint, project=project, token=\"${script.token}\")\nlaunch = service.start_launch(name=launch_name, start_time=timestamp(), description=launch_doc, mode=\"DEBUG\", attributes=attributes)\nitem_id = service.start_test_item(name=\"Open ports\", start_time=timestamp(), item_type=\"STEP\")\nservice.log(time=timestamp(), message=\"${result}\", level=\"INFO\")\nservice.finish_test_item(item_id=item_id, end_time=timestamp(), status=\"PASSED\")\nservice.finish_launch(end_time=timestamp())\nservice.terminate()' > test.py",
                        returnStdout: true
                    ).trim()
                    script.sh(
                        script: "python test.py",
                        returnStdout: true
                    ).trim()
                    
                }
            }      
        }
    }
}
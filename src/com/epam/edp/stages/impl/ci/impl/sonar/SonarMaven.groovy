/* Copyright 2019 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License.*/

package com.epam.edp.stages.impl.ci.impl.sonar


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import com.epam.edp.stages.impl.ci.impl.sonarcleanup.SonarCleanupApplicationLibrary
import org.apache.commons.lang.RandomStringUtils

@Stage(name = "sonar", buildTool = ["maven"], type = [ProjectType.APPLICATION, ProjectType.AUTOTESTS, ProjectType.LIBRARY])
class SonarMaven {

    def getSonarReportJson(context, projectKey='petclinic', serviceBranch = 'master', sonarUrl = "https://sonar-oc-green-sk-dev-dev.delivery.aws.main.edp.projects.epam.com/") {
        String sonarAnalysisStatus
        def sonarJsonReportLink = "${sonarUrl}/api/issues/search?componentKeys=${projectKey}&branch=${serviceBranch}&resolved=false&facets=severities"
        def sonarReportMap = script.readProperties file: "${context.workDir}/target/sonar/report-task.txt"

        script.println("[JENKINS][DEBUG] Waiting for report from Sonar")
        script.timeout(time: 10, unit: 'MINUTES') {
            while (sonarAnalysisStatus != 'SUCCESS') {
                if (sonarAnalysisStatus == 'FAILED') {
                    script.error "[JENKINS][ERROR] Sonar analysis finished with status: \'${sonarAnalysisStatus}\'"
                }
                response = script.httpRequest acceptType: 'APPLICATION_JSON',
                        url: sonarReportMap.ceTaskUrl,
                        httpMode: 'GET',
                        quiet: true

                content = readJSON text: response.content
                sonarAnalysisStatus = content.task.status
                println("[JENKINS][DEBUG] Current status: " + sonarAnalysisStatus)
            }
        }

        script.httpRequest acceptType: 'APPLICATION_JSON',
                    url: sonarJsonReportLink,
                    httpMode: 'GET',
                    outputFile: "${context.workDir}/target/sonar/sonar-report.json"
    }

    Script script

    void run(context) {
        def codereviewAnalysisRunDir = context.workDir
        if (context.job.type == "codereview") {
            codereviewAnalysisRunDir = new File("${context.workDir}/../${RandomStringUtils.random(10, true, true)}")

            script.dir("${codereviewAnalysisRunDir}") {
                if (script.fileExists("${context.workDir}/target")) {
                    script.println("[JENKINS][DEBUG] Project with usual structure")
                    script.sh """
              export LANG=en_US.utf-8
              cd ${context.workDir}
              git config --local core.quotepath false
              IFS=\$'\\n';for i in \$(git diff --diff-filter=ACMR --name-only origin/master); \
                do cp --parents \"\$i\" ${codereviewAnalysisRunDir}/; done
              cp -f pom.xml ${codereviewAnalysisRunDir}/
              cp --parents -r src/test/ ${codereviewAnalysisRunDir}
              cp --parents -r target/ ${codereviewAnalysisRunDir}
              """
                } else {
                    script.println("[JENKINS][DEBUG] Multi-module project")
                    script.sh """
              mkdir -p ${codereviewAnalysisRunDir}/unittests
              cd ${context.workDir}
              IFS=\$'\\n';for i in \$(git diff --diff-filter=ACMR --name-only origin/master); \
                do cp --parents \"\$i\" ${codereviewAnalysisRunDir}/; done
              for directory in `find . -type d -name \'test\'`; do cp --parents -r \${directory} \
              ${codereviewAnalysisRunDir}/unittests; done
              for poms in `find . -type f -name \'pom.xml\'`; do cp --parents -r \${poms} \
              ${codereviewAnalysisRunDir}; done
              for targets in `find . -type d -name \'target\'`; do cp --parents -r \${targets} \
              ${codereviewAnalysisRunDir}; done
              """
                }
            }

        }

        script.dir("${codereviewAnalysisRunDir}") {
            script.withSonarQubeEnv('Sonar') {
                script.withCredentials([script.usernamePassword(credentialsId: "${context.nexus.credentialsId}",
                        passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                    script.sh "${context.buildTool.command} ${context.buildTool.properties} -Dartifactory.username=${script.USERNAME} -Dartifactory.password=${script.PASSWORD} " +
                            "sonar:sonar " +
                            "-Dsonar.projectKey=${context.codebase.name} " +
                            "-Dsonar.projectName=${context.codebase.name} "
                }
            }
            script.timeout(time: 10, unit: 'MINUTES') {
                def qualityGateResult = script.waitForQualityGate()
                if (qualityGateResult.status != 'OK')
                    getSonarReportJson(context)
                    script.error "[JENKINS][ERROR] Sonar quality gate check has been failed with status " +
                            "${qualityGateResult.status}"
            }
        }

        if (context.job.type == "build")
            new SonarCleanupApplicationLibrary(script: script).run(context)
    }
}

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

@Stage(name = "sonar", buildTool = ["dotnet"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class SonarDotnetApplicationLibrary {
    def getSonarReportJson(context, codereviewAnalysisRunDir) {
        String sonarAnalysisStatus
        def sonarReportMap = script.readProperties file: "${codereviewAnalysisRunDir}/target/sonar/report-task.txt"
        def sonarJsonReportLink = "${context.sonar.route}/api/issues/search?componentKeys=${context.codebase.name}:change-${context.git.changeNumber}-${context.git.patchsetNumber}&branch=${context.git.branch}&resolved=false&facets=severities"

        script.println("[JENKINS][DEBUG] Sonar ProjectKey - ${context.codebase.name}:change-${context.git.changeNumber}-${context.git.patchsetNumber}")
        script.println("[JENKINS][DEBUG] Branch - ${context.git.branch}")
        script.println("[JENKINS][DEBUG] SONAR URL - ${context.sonar.route}")
        script.println("[JENKINS][DEBUG] RUN DIR - ${codereviewAnalysisRunDir}")

        script.println("[JENKINS][DEBUG] Waiting for report from Sonar")
        script.timeout(time: 10, unit: 'MINUTES') {
            while (sonarAnalysisStatus != 'SUCCESS') {
                if (sonarAnalysisStatus == 'FAILED') {
                    script.error "[JENKINS][ERROR] Sonar analysis finished with status: \'${sonarAnalysisStatus}\'"
                }
                def response = script.httpRequest acceptType: 'APPLICATION_JSON',
                        url: sonarReportMap.ceTaskUrl,
                        httpMode: 'GET',
                        quiet: true

                def content = script.readJSON text: response.content
                sonarAnalysisStatus = content.task.status
                script.println("[JENKINS][DEBUG] Current status: " + sonarAnalysisStatus)
            }
        }

        script.httpRequest acceptType: 'APPLICATION_JSON',
                    url: sonarJsonReportLink,
                    httpMode: 'GET',
                    outputFile: "${codereviewAnalysisRunDir}/target/sonar/sonar-report.json"
        sendReport(context, codereviewAnalysisRunDir)
    }

    def sendReport(context, codereviewAnalysisRunDir) {
        script.dir("${codereviewAnalysisRunDir}") {
            script.println("[JENKINS][DEBUG] SONAR URL - ${context.sonar.route}")
            script.sh """pwd"""
            script.sonarToGerrit inspectionConfig: [baseConfig: [projectPath: "", sonarReportPath: "target/sonar/sonar-report.json"], serverURL: "${context.sonar.route}"],
                    notificationConfig: [commentedIssuesNotificationRecipient: 'NONE', negativeScoreNotificationRecipient: 'NONE'],
                    reviewConfig: [issueFilterConfig: [newIssuesOnly: false, changedLinesOnly: false, severity: 'CRITICAL']],
                    scoreConfig: [category: 'Code-Review', noIssuesScore: +1, issuesScore: -1, issueFilterConfig: [severity: 'CRITICAL']]
        }
    }
    Script script

    void run(context) {
        def scannerHome = script.tool 'SonarScannerMSBuild'
        if (context.job.type == "codereview") {
            script.dir("${context.workDir}") {
                script.withSonarQubeEnv('Sonar') {
                    script.sh """
                    dotnet ${scannerHome}/SonarScanner.MSBuild.dll begin /k:${context.codebase.name} \
                    /k:${context.codebase.name}:change-${context.git.changeNumber}-${context.git.patchsetNumber} \
                    /n:${context.codebase.name}:change-${context.git.changeNumber}-${context.git.patchsetNumber} \
                    /d:sonar.cs.opencover.reportsPaths=${context.workDir}/*Tests*/*.xml
                    dotnet build ${context.buildTool.sln_filename}
                    dotnet ${scannerHome}/SonarScanner.MSBuild.dll end
                """
                }
                script.timeout(time: 10, unit: 'MINUTES') {
                    def qualityGateResult = script.waitForQualityGate()
                    if (qualityGateResult.status != 'OK')
                        script.error "[JENKINS][ERROR] Sonar quality gate check has been failed with status " +
                                "${qualityGateResult.status}"
                }
            }
        } else {
             script.dir("${context.workDir}") {
                 script.withSonarQubeEnv('Sonar') {
                     script.sh """
                     dotnet ${scannerHome}/SonarScanner.MSBuild.dll begin /k:${context.codebase.name} \
                     /k:${context.codebase.name} \
                     /n:${context.codebase.name} \
                     /d:sonar.cs.opencover.reportsPaths=${context.workDir}/*Tests*/*.xml
                     dotnet build ${context.buildTool.sln_filename}
                     dotnet ${scannerHome}/SonarScanner.MSBuild.dll end
                 """
                 }
                 script.timeout(time: 10, unit: 'MINUTES') {
                     def qualityGateResult = script.waitForQualityGate()
                     if (qualityGateResult.status != 'OK')
                         script.error "[JENKINS][ERROR] Sonar quality gate check has been failed with status " +
                                 "${qualityGateResult.status}"
                 }
             }
        }
        if (context.job.type == "build")
            new SonarCleanupApplicationLibrary(script: script).run(context)
        }
    }
}

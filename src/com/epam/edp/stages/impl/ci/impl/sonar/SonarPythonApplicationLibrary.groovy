/* Copyright 2020 EPAM Systems.

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

@Stage(name = "sonar", buildTool = "python", type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class SonarPythonApplicationLibrary {
    Script script

    def sendSonarScan(workDir, codebaseName) {
        def scannerHome = script.tool 'SonarQube Scanner'
        script.dir("${workDir}") {
            script.withSonarQubeEnv('Sonar') {
                script.sh "pylint --exit-zero *.py -r n --msg-template=\"{path}:{line}: [{msg_id}({symbol}), {obj}] {msg}\" > pylint-reports.txt"
                script.sh "pytest --cov=. --cov-report xml:coverage.xml"
                script.sh "${scannerHome}/bin/sonar-scanner " +
                          "-Dsonar.projectKey=${context.codebase.name} " +
                          "-Dsonar.projectName=${context.codebase.name} " +
                          "-Dsonar.language=py " +
                          "-Dsonar.python.pylint.reportPath=${context.workDir}/pylint-reports.txt " +
                          "-Dsonar.sourceEncoding=UTF-8 "
            }
        }
    }

    void run(context) {
        sendSonarScan(context.workDir, "${context.codebase.name}:change-${context.git.changeNumber}-${context.git.patchsetNumber}")
            script.dir("${context.workDir}") {


    }
}
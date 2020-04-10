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

package com.epam.edp.stages.impl.ci.impl.push

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "push", buildTool = ["gradle"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class PushGradleApplicationLibrary {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            script.dir("${context.workDir}") {
                script.withCredentials([script.usernamePassword(credentialsId: "${context.nexus.credentialsId}",
                        passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                    def nexusRepositoryUrl = context.codebase.version.toLowerCase().contains("snapshot") ?
                            "${context.buildTool.hostedRepository}-snapshots" : "${context.buildTool.hostedRepository}-releases"
                    script.sh "gradle -Dorg.gradle.internal.publish.checksums.insecure=true publish " +
                            "-I ${context.buildTool.settings} -PnexusLogin=${script.USERNAME} " +
                            "-PnexusPassword=${script.PASSWORD} -PnexusMavenRepositoryUrl=${nexusRepositoryUrl}"
                }
            }
        }
    }
}
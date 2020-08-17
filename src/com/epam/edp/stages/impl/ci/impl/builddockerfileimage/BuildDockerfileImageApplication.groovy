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

package com.epam.edp.stages.impl.ci.impl.builddockerfileimage

import com.epam.edp.stages.impl.ci.impl.codebaseiamgestream.CodebaseImageStreams

class BuildDockerfileImageApplication {
    Script script

    void run(context) {
        if (!script.fileExists("${context.workDir}/Dockerfile"))
            script.error "[JENKINS][ERROR] There is no Dockerfile in the root directory of the project ${context.codebase.name}. "


        def dockerRegistryHost = "docker-registry.default.svc:5000"

        def buildconfigName = "${context.codebase.name}-dockerfile-${context.git.branch.replaceAll("[^\\p{L}\\p{Nd}]+", "-")}"
        def outputImagestreamName = "${context.codebase.name}-${context.git.branch.replaceAll("[^\\p{L}\\p{Nd}]+", "-")}"
        context.codebase.imageBuildArgs.push("--name=${buildconfigName}")
        context.codebase.imageBuildArgs.push("--to=${dockerRegistryHost}/${script.openshift.project()}/${outputImagestreamName}:${context.codebase.isTag}")
        def resultTag
        script.openshift.withCluster() {
            script.openshift.withProject() {
                /*if (!script.openshift.selector("buildconfig", "${buildconfigName}").exists())
                    script.openshift.newBuild(context.codebase.imageBuildArgs)*/

                script.openshift.newBuild(context.codebase.imageBuildArgs)
                script.dir(context.codebase.deployableModuleDir) {
                    if ("${context.workDir}" != "${context.codebase.deployableModuleDir}") {
                        script.sh "cp ${context.workDir}/Dockerfile ${context.codebase.deployableModuleDir}/"
                    }
                    script.sh "tar -cf ${context.codebase.name}.tar *"
                    def buildResult = script.openshift.selector("bc", "${buildconfigName}").startBuild(
                            "--from-archive=${context.codebase.name}.tar",
                            "--wait=true")
                    resultTag = buildResult.object().status.output.to.imageDigest
                }
                script.println("[JENKINS][DEBUG] Build config ${context.codebase.name} with result " +
                        "${buildconfigName}:${resultTag} has been completed")

                new CodebaseImageStreams(context, script)
                        .UpdateOrCreateCodebaseImageStream(outputImagestreamName, "${dockerRegistryHost}/${outputImagestreamName}", context.codebase.isTag)
            }
        }
    }
}

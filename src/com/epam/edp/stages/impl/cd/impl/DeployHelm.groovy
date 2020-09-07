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

package com.epam.edp.stages.impl.cd.impl

import com.epam.edp.stages.impl.cd.Stage
import org.apache.commons.lang.RandomStringUtils
import groovy.json.JsonSlurperClassic

@Stage(name = "deploy-helm")
class DeployHelm {
    Script script


    def deployCodebaseTemplate(context, codebase, deployTemplatesPath) {
        def templateName = "Chart"
        if (!checkTemplateExists(templateName, deployTemplatesPath)) {
            return
        }

        codebase.cdPipelineName = context.job.pipelineName
        codebase.cdPipelineStageName = context.job.stageName

        def imageName = codebase.inputIs ? codebase.inputIs : codebase.normalizedName
        def parametersMap = [
                ['name': 'namespace', 'value': "${context.job.deployProject}"],
                ['name': 'cdPipelineName', 'value': "${codebase.cdPipelineName}"],
                ['name': 'cdPipelineStageName', 'value': "${codebase.cdPipelineStageName}"],
                ['name': 'image.name', 'value': "${context.environment.config.dockerRegistryHost}/${imageName}"],
                ['name': 'image.version', 'value': "${codebase.version}"],
                ['name': 'database.required', 'value': "${codebase.db_kind != "" ? true : false}"],
                ['name': 'database.version', 'value': "${codebase.db_version}"],
                ['name': 'database.capacity', 'value': "${codebase.db_capacity}"],
                ['name': 'database.database.storageClass', 'value': "${codebase.db_storage}"],
                ['name': 'ingress.path', 'value': "${codebase.route_path}"],
                ['name': 'ingress.site', 'value': "${codebase.route_site}"],
                ['name': 'dnsWildcard', 'value': "${context.job.dnsWildcard}"],
        ]

        context.platform.deployCodebase(
                context.job.deployProject,
                "${deployTemplatesPath}",
                codebase,
                "${context.environment.config.dockerRegistryHost}/${imageName}",
                context.job.deployTimeout,
                parametersMap
        )
    }


    def deployCodebase(version, name, context, codebase) {
        def codebaseDir = "${script.WORKSPACE}/${RandomStringUtils.random(10, true, true)}/${name}"
        def deployTemplatesPath = "${codebaseDir}/${context.job.deployTemplatesDirectory}"
        script.dir("${codebaseDir}") {
            if (!cloneProject(context, codebase)) {
                if (codebase.name in context.job.applicationsToPromote)
                    context.job.applicationsToPromote.remove(codebase.name)
                return
            }
            deployConfigMaps(codebaseDir, name, context)
            try {
                deployCodebaseTemplate(context, codebase, deployTemplatesPath)
            }
            catch (Exception ex) {
                script.println("[JENKINS][WARNING] Deployment of codebase ${name} has been failed.\r\nReason - ${ex}.\r\nTrace: ${ex.getStackTrace().collect { it.toString() }.join('\n')}")
                script.currentBuild.result = 'UNSTABLE'
                context.platform.rollbackDeployedCodebase(codebase.name, context.job.deployProject)
                if (codebase.name in context.job.applicationsToPromote)
                    context.job.applicationsToPromote.remove(codebase.name)
            }
        }
    }

    void run(context) {
        context.platform.createProjectIfNotExist(context.job.deployProject, context.job.edpName)
        def secretSelector = context.platform.getObjectList("secret")

        secretSelector.each() { secret ->
            def newSecretName = secret.replace(context.job.sharedSecretsMask, '')
            if (secret =~ /${context.job.sharedSecretsMask}/)
                if (!context.platform.checkObjectExists('secrets', newSecretName))
                    context.platform.copySharedSecrets(secret, newSecretName, context.job.deployProject)
        }

        if (context.job.buildUser == null || context.job.buildUser == "")
            context.job.buildUser = getBuildUserFromLog(context)

        if (context.job.buildUser != null && context.job.buildUser != "") {
            context.platform.createRoleBinding(context.job.buildUser, context.job.deployProject)
        }

        def deployCodebasesList = context.job.codebasesList.clone()
        while (!deployCodebasesList.isEmpty()) {
            def parallelCodebases = [:]
            def tempAppList = getNElements(deployCodebasesList, context.job.maxOfParallelDeployApps)

            tempAppList.each() { codebase ->
                if ((codebase.version == "No deploy") || (codebase.version == "noImageExists")) {
                    script.println("[JENKINS][WARNING] Application ${codebase.name} deploy skipped")
                    return
                }

                if (codebase.version == "latest") {
                    codebase.version = codebase.latest
                    script.println("[JENKINS][DEBUG] Latest tag equals to ${codebase.latest} version")
                    if (!codebase.version)
                        return
                }

                if (codebase.version == "stable") {
                    codebase.version = codebase.stable
                    script.println("[JENKINS][DEBUG] Stable tag equals to ${codebase.stable} version")
                    if (!codebase.version)
                        return
                }

                if (!checkImageExists(context, codebase))
                    return

                context.environment.config.dockerRegistryHost = getDockerRegistryInfo(context)
                parallelCodebases["${codebase.name}"] = {
                    deployCodebase(codebase.version, codebase.name, context, codebase)
                }
            }
            script.parallel parallelCodebases
        }
    }
}
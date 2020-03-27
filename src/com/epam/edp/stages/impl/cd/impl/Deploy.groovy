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

@Stage(name = "deploy")
class Deploy {
    Script script

    def getBuildUserFromLog(context) {
        def jenkinsCred = "admin:${context.jenkins.token}".bytes.encodeBase64().toString()
        def jobUrl = "${context.job.buildUrl}".replaceFirst("${context.job.jenkinsUrl}", '')
        def response = script.httpRequest url: "http://jenkins.${context.job.ciProject}:8080/${jobUrl}consoleText",
                httpMode: 'GET',
                customHeaders: [[name: 'Authorization', value: "Basic ${jenkinsCred}"]]
        return script.sh(
                script: "#!/bin/sh -e\necho \"${response.content}\" | grep \"Approved by\" -m 1 | awk {'print \$3'}",
                returnStdout: true
        ).trim()
    }

    def checkOpenshiftTemplateExists(context, templateName) {
        if (!script.openshift.selector("template", templateName).exists()) {
            script.println("[JENKINS][WARNING] Template which called ${templateName} doesn't exist in ${context.job.ciProject} namespace")
            return false
        }
        return true
    }

    def deployConfigMaps(codebaseDir, name, context) {
        File folder = new File("${codebaseDir}/config-files")
        for (file in folder.listFiles()) {
            if (file.isFile() && file.getName() == "Readme.md")
                continue
            String configsDir = file.getName().split("\\.")[0].replaceAll("[^\\p{L}\\p{Nd}]+", "-").toLowerCase()
            context.platform.createConfigMapFromFile("${name}-${configsDir}", context.job.deployProject, "${codebaseDir}/config-files/${file.getName()}")
            script.println("[JENKINS][DEBUG] Configmap ${configsDir} has been created")
        }
    }

    def checkDeployment(context, codebaseName, type, codebaseKind = null) {
        script.println("[JENKINS][DEBUG] Validate deployment - ${codebaseName} in ${context.job.deployProject}")
        try {
            context.platform.verifyDeployedCodebase(codebaseName, context.job.deployProject, codebaseKind)
            script.println("[JENKINS][DEBUG] Workload ${codebaseName} in project ${context.job.deployProject} has been rolled out")
        }
        catch (Exception verifyDeploymentException) {
            script.println("[JENKINS][WARNING] Rolling out of ${codebaseName} has been failed.")
            if (type == "application") {
                context.platform.rollbackDeployedCodebase(codebaseName, context.job.deployProject, codebaseKind)
                context.platform.verifyDeployedCodebase(codebaseName, context.job.deployProject, codebaseKind)
            }
            throw (verifyDeploymentException)
        }
    }

    def checkImageExists(context, object) {
        def imageExists = context.platform.getImageStream(object.inputIs, context.job.crApiVersion)
        if (imageExists == "") {
            script.println("[JENKINS][WARNING] Image stream ${object.name} doesn't exist in the project ${context.job.ciProject}\r\n" +
                    "[JENKINS][WARNING] Deploy will be skipped")
            return false
        }

        def tagExist = context.platform.getImageStreamTags(object.inputIs, context.job.crApiVersion)
        if (!tagExist) {
            script.println("[JENKINS][WARNING] Image stream ${object.name} with tag ${object.version} doesn't exist in the project ${context.job.ciProject}\r\n" +
                    "[JENKINS][WARNING] Deploy will be skipped")
            return false
        }
        return true
    }

    def getNumericVersion(context, codebase) {
        def hash = script.sh(
                script: "oc -n ${context.job.ciProject} get is ${codebase.inputIs} -o jsonpath=\'{@.spec.tags[?(@.name==\"${codebase.version}\")].from.name}\'",
                returnStdout: true
        ).trim()
        def tags = script.sh(
                script: "oc -n ${context.job.ciProject} get is ${codebase.inputIs} -o jsonpath=\'{@.spec.tags[?(@.from.name==\"${hash}\")].name}\'",
                returnStdout: true
        ).trim().tokenize()
        tags.removeAll { it == "latest" }
        tags.removeAll { it == "stable" }
        script.println("[JENKINS][DEBUG] Codebase ${codebase.name} has the following numeric tag, which corresponds to tag ${codebase.version} - ${tags}")
        switch (tags.size()) {
            case 0:
                script.println("[JENKINS][WARNING] Codebase ${codebase.name} has no numeric version for tag ${codebase.version}\r\n" +
                        "[JENKINS][WARNING] Deploy will be skipped")
                return null
                break
            case 1:
                return (tags[0])
                break
            default:
                script.println("[JENKINS][WARNING] Codebase ${codebase.name} has more than one numeric tag for tag ${codebase.version}\r\n" +
                        "[JENKINS][WARNING] We will use the first one")
                return (tags[0])
                break
        }
    }

    def getRepositoryPath(codebase) {
        if (codebase.strategy == "import") {
            return codebase.gitProjectPath
        }
        return "/" + codebase.name
    }

    def getRefspec(codebase) {
        return codebase.versioningType == "edp" ?
            "refs/tags/build/${codebase.version}" :
            "refs/tags/${codebase.version}"
    }

    def cloneProject(context, codebase) {
        script.println("[JENKINS][DEBUG] Start fetching Git Server info for ${codebase.name} from ${codebase.gitServer} CR")
        def gitServerName = "gitservers.${context.job.crApiVersion}.edp.epam.com"

        script.println("[JENKINS][DEBUG] Git Server CR Version: ${context.job.crApiVersion}")
        script.println("[JENKINS][DEBUG] Git Server Name: ${gitServerName}")

        def autouser = context.platform.getJsonPathValue(gitServerName, codebase.gitServer, ".spec.gitUser")
        def host = context.platform.getJsonPathValue(gitServerName, codebase.gitServer, ".spec.gitHost")
        def sshPort = context.platform.getJsonPathValue(gitServerName, codebase.gitServer, ".spec.sshPort")
        def credentialsId = context.platform.getJsonPathValue(gitServerName, codebase.gitServer, ".spec.nameSshKeySecret")

        script.println("[JENKINS][DEBUG] autouser: ${autouser}")
        script.println("[JENKINS][DEBUG] host: ${host}")
        script.println("[JENKINS][DEBUG] sshPort: ${sshPort}")
        script.println("[JENKINS][DEBUG] credentialsId: ${credentialsId}")

        def repoPath = getRepositoryPath(codebase)
        script.println("[JENKINS][DEBUG] Repository path: ${repoPath}")

        def gitCodebaseUrl = "ssh://${autouser}@${host}:${sshPort}${repoPath}"

        try {
            def refspec = getRefspec(codebase)
            script.checkout([$class                           : 'GitSCM', branches: [[name: "${refspec}"]],
                             doGenerateSubmoduleConfigurations: false, extensions: [],
                             submoduleCfg                     : [],
                             userRemoteConfigs                : [[credentialsId: "${credentialsId}",
                                                                  refspec      : "${refspec}",
                                                                  url          : "${gitCodebaseUrl}"]]])
        }
        catch (Exception ex) {
            script.unstable("[JENKINS][WARNING] Project ${codebase.name} cloning has failed with ${ex}\r\n" +
                    "[JENKINS][WARNING] Deploy will be skipped\r\n" +
                    "[JENKINS][WARNING] Check if tag ${codebase.version} exists in repository")
            script.currentBuild.setResult('UNSTABLE')
            script.currentBuild.description = "${script.currentBuild.description}\r\n${codebase.name} deploy failed"
            return false
        }
        script.println("[JENKINS][DEBUG] Project ${codebase.name} has been successfully cloned")
        return true
    }

    def getDeploymentWorkloadsList(deploymentTemplate, isSvc = false) {
        def deploymentWorkloadsList = []
        ["Deployment", "DeploymentConfig"].each() { kind ->
            def workloads = script.sh(
                    script: "oc process ${isSvc ? "" : "-f"} ${deploymentTemplate} " +
                    "${isSvc ? "" : "-p IMAGE_NAME=fake "}" +
                    "${isSvc ? "" : "-p NAMESPACE=fake "}" +
                    "${isSvc ? "-p SERVICE_VERSION=fake " : "-p APP_VERSION=fake "}" +
                    "-o jsonpath='{range .items[?(@.kind==\"${kind}\")]}{.kind}{\"/\"}{.metadata.name}{\"\\n\"}{end}'",
                    returnStdout: true
            ).trim().tokenize("\n")
            workloads.each() {
                def workloadMap = [:]
                workloadMap["kind"] = it.split("/")[0]
                workloadMap["name"] = it.split("/")[1]
                deploymentWorkloadsList.add(workloadMap)
            }
        }
        return deploymentWorkloadsList
    }

    def deployCodebaseTemplate(context, codebase, deployTemplatesPath) {
        def templateName = "${codebase.name}-install-${context.job.stageWithoutPrefixName}"

        if (codebase.need_database)
            script.sh("oc adm policy add-scc-to-user anyuid -z ${codebase.name} -n ${context.job.deployProject}")

        if (!checkTemplateExists(templateName, deployTemplatesPath)) {
            script.println("[JENKSIN][INFO] Trying to find out default template ${codebase.name}.yaml")
            templateName = codebase.name
            if (!checkTemplateExists(templateName, deployTemplatesPath))
                return
        }

        def imageName = codebase.inputIs ? codebase.inputIs : codebase.normalizedName
        def deploymentWorkloadsList = getDeploymentWorkloadsList("${deployTemplatesPath}/${templateName}.yaml", false)
        context.platform.deployCodebase(
                context.job.deployProject,
                "${deployTemplatesPath}/${templateName}.yaml",
                "${context.job.ciProject}/${imageName}",
                codebase, context.job.dnsWildcard
        )
        deploymentWorkloadsList.each() { workload ->
            checkDeployment(context, workload.name, 'application', workload.kind)
        }
    }

    def checkTemplateExists(templateName, deployTemplatesPath) {
        def templateYamlFile = new File("${deployTemplatesPath}/${templateName}.yaml")
        if (!templateYamlFile.exists()) {
            script.println("[JENKINS][WARNING] Template file which called ${templateName}.yaml doesn't exist in ${deployTemplatesPath} in the repository")
            return false
        }
        return true
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
                script.unstable("[JENKINS][WARNING] Deployment of codebase ${name} has been failed. Reason - ${ex}.")
                script.currentBuild.setResult('UNSTABLE')
                if (codebase.name in context.job.applicationsToPromote)
                    context.job.applicationsToPromote.remove(codebase.name)
            }
        }
    }

    def getNElements(entities, max_apps) {
        def tempEntityList = entities.stream()
                .limit(max_apps.toInteger())
                .collect()
        entities.removeAll(tempEntityList)

        return tempEntityList
    }

    void run(context) {
        script.openshift.withCluster() {
            context.platform.createProjectIfNotExist(context.job.deployProject, context.job.edpName)
            def secretSelector = context.platform.getObjectList("secret")

            secretSelector.withEach { secret ->
                def sharedSecretName = secret.name().split('/')[1]
                def secretName = sharedSecretName.replace(context.job.sharedSecretsMask, '')
                if (sharedSecretName =~ /${context.job.sharedSecretsMask}/)
                    if (!context.platform.checkObjectExists('secrets', secretName))
                        context.platform.copySharedSecrets(sharedSecretName, secretName, context.job.deployProject)
            }

            if (context.job.buildUser == null || context.job.buildUser == "")
                context.job.buildUser = getBuildUserFromLog(context)

            if (context.job.buildUser != null && context.job.buildUser != "") {
                context.platform.createRoleBinding(context.job.buildUser, context.job.deployProject)
            }

            while (!context.job.servicesList.isEmpty()) {
                def parallelServices = [:]
                def tempServiceList = getNElements(context.job.servicesList, context.job.maxOfParallelDeployServices)

                tempServiceList.each() { service ->
                    if (!checkOpenshiftTemplateExists(context, service.name))
                        return

                    script.sh("oc adm policy add-scc-to-user anyuid -z ${service.name} -n ${context.job.deployProject}")

                    def deploymentWorkloadsList = getDeploymentWorkloadsList(service.name, true)
                    parallelServices["${service.name}"] = {
                        script.sh("oc -n ${context.job.ciProject} process ${service.name} " +
                                "-p SERVICE_VERSION=${service.version} " +
                                "-o json | oc -n ${context.job.deployProject} apply -f -")
                        deploymentWorkloadsList.each() { workload ->
                            checkDeployment(context, workload.name, 'service', workload.kind)
                        }
                    }
                }

                script.parallel parallelServices
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

                    script.sh("oc adm policy add-scc-to-user anyuid -z ${codebase.name} -n ${context.job.deployProject}")
                    script.sh("oc adm policy add-role-to-user view system:serviceaccount:${context.job.deployProject}:${codebase.name} -n ${context.job.deployProject}")

                    parallelCodebases["${codebase.name}"] = {
                        deployCodebase(codebase.version, codebase.name, context, codebase)
                    }
                }
                script.parallel parallelCodebases
            }
        }
    }
}
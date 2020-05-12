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

package com.epam.edp.stages.impl.ci.impl.jirafixversion

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage
import com.github.jenkins.lastchanges.pipeline.LastChangesPipelineGlobal
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import hudson.FilePath

@Stage(name = "jira-fix-version", buildTool = ["maven", "npm", "dotnet", "gradle", "any"], type = [ProjectType.APPLICATION, ProjectType.AUTOTESTS, ProjectType.LIBRARY])
class JiraFixVersion {
    Script script

    def getChanges(workDir) {
        script.dir("${workDir}") {
            def publisher = new LastChangesPipelineGlobal(script).getLastChangesPublisher "LAST_SUCCESSFUL_BUILD", "SIDE", "LINE", true, true, "", "", "", "", ""
            publisher.publishLastChanges()
            return publisher.getLastChanges()
        }
    }

    def getJiraFixTemplate(platform) {
        script.println("[JENKINS][DEBUG] Getting JiraFixVersion CR template")
        def temp = platform.getJsonPathValue("cm", "jfv-template", ".data.jfv\\.json")
        script.println("[JENKINS][DEBUG] TEmplate ${temp}")
        return new JsonSlurperClassic().parseText(temp)
    }

    def addCommitId(template, id) {
        if (template.spec.commits == "replace") {
            template.spec.commits = []
        }
        template.spec.commits.add(id)
    }

    def addTicketNumber(template, tickets) {
        if (template.spec.tickets == "replace") {
            template.spec.tickets = []
        }
        template.spec.tickets.addAll(tickets)
    }

    def parseJiraFixVersionTemplate(template, name, changes, pattern) {
        script.println("[JENKINS][DEBUG] Parsing JiraFixVersion template")
        template.metadata.name = name
        for (commit in changes.getCommits()) {
            def info = commit.getCommitInfo()
            script.println("[JENKINS][DEBUG] Commit message ${info.getCommitMessage()}")
            addCommitId(template, info.getCommitId())
            addTicketNumber(template, info.getCommitMessage().findAll(pattern))
        }
        return JsonOutput.toJson(template)
    }

    def createJiraFixVersionCR(platform, path) {
        script.println("[JENKINS][DEBUG] Trying to create JiraFixVersion CR")
        platform.apply(path.getRemote())
        script.println("[JENKINS][INFO] JiraFixVersion CR has been created")
    }

    def saveTemplateToFile(outputFilePath, template) {
        def jiraFixVersionTemplateFile = new FilePath(Jenkins.getInstance().
                getComputer(script.env['NODE_NAME']).
                getChannel(), outputFilePath)
        jiraFixVersionTemplateFile.write(template, null)
        return jiraFixVersionTemplateFile
    }

    void run(context) {
        def ticketNamePattern = context.codebase.config.ticketNamePattern
        script.println("[JENKINS][DEBUG] Ticket name pattern has been fetched ${ticketNamePattern}")
        def changes = getChanges(context.workDir)
        def template = getJiraFixTemplate(context.platform)
        def parsedTemplate = parseJiraFixVersionTemplate(template,
                "${context.codebase.config.name}-${context.codebase.isTag}", changes, ticketNamePattern)
        def filePath = saveTemplateToFile("${context.workDir}/jfv-template.json", parsedTemplate)
        createJiraFixVersionCR(context.platform, filePath)
    }

}
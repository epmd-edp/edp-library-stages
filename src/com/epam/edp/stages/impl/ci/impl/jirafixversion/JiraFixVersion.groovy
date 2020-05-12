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

@Stage(name = "jira-fix-version", buildTool = ["maven", "npm", "dotnet", "gradle", "any"], type = [ProjectType.APPLICATION, ProjectType.AUTOTESTS, ProjectType.LIBRARY])
class JiraFixVersion {
    Script script

    def getLastSuccessfulCommitId() {
        def lastSuccessfulBuild = script.currentBuild.rawBuild.getPreviousSuccessfulBuild()
        return lastSuccessfulBuild ? commitHashForBuild(lastSuccessfulBuild) : null
    }

    def getCurrentCommitId(workDir) {
        script.dir("${workDir}") {
            return script.sh(
                    script: "git log --format=%H -n 1",
                    returnStdout: true
            ).trim()
        }
    }

    def getLogHistoryInRange(workDir, start, end) {
        script.dir("${workDir}") {
            return script.sh(
                    script: "git log ${start}...${end}",
                    returnStdout: true
            ).trim()
        }
    }

    def commitHashForBuild(build) {
        return build.getAction(hudson.plugins.git.util.BuildData.class).lastBuiltRevision.sha1String
    }

    def getChangesSinceLastSuccessfulBuild() {
        def changes = []
        def build = script.currentBuild

        while (build != null && build.result != 'SUCCESS') {
            changes += (build.changeSets.collect { changeSet ->
                (changeSet.items.collect { item ->
                    (item.affectedFiles.collect { affectedFile ->
                        affectedFile.path
                    }).flatten()
                }).flatten()
            }).flatten()

            build = build.previousBuild
        }

        return changes.unique()
    }

    void run(context) {
        script.println("[JENKINS][DEBUG]-------------------------------------------")
        def res = getChangesSinceLastSuccessfulBuild()
        script.println("[JENKINS][DEBUG]-------------------------------------------${res}")
       /* def changeLogSets = script.currentBuild.rawBuild.changeSets
        for (int i = 0; i < changeLogSets.size(); i++) {
            def entries = changeLogSets[i].items
            for (int j = 0; j < entries.length; j++) {
                def entry = entries[j]
                script.println("${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg}")
                def files = new ArrayList(entry.affectedFiles)
                for (int k = 0; k < files.size(); k++) {
                    def file = files[k]
                    script.println("  ${file.editType.name} ${file.path}")
                }
            }
        }*/

        /* def publisher = LastChanges.getLastChangesPublisher "LAST_SUCCESSFUL_BUILD", "SIDE", "LINE", true, true, "", "", "", "", ""
         publisher.publishLastChanges()
         def changes = publisher.getLastChanges()
         println(changes.getEscapedDiff())
         for (commit in changes.getCommits()) {
             println(commit)
             def commitInfo = commit.getCommitInfo()
             println(commitInfo)
             println(commitInfo.getCommitMessage())
             println(commit.getChanges())
         }*/
    }

/*    void run(context) {
        def currentCommitId = getCurrentCommitId(context.workDir)
        script.println("[JENKINS][DEBUG] Current commit id ${currentCommitId}")
        def lastCommitId = getLastSuccessfulCommitId()
        script.println("[JENKINS][DEBUG] Last successfull commit id ${lastCommitId}")
        def log = getLogHistoryInRange(context.workDir, lastCommitId, currentCommitId)
//        context.codebase.config.commitMessagePattern
        def tickets = log.findAll("\\[EPMDEDP-\\d{4}\\]")
        script.println("[JENKINS][DEBUG] Tickets in range ${tickets}")
    }*/
}
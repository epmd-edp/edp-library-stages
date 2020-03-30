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

package com.epam.edp.stages.impl.ci.impl.gittag


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "git-tag", buildTool = ["gradle", "maven", "dotnet", "npm", "any"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class GitTagApplicationLibrary {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            script.withCredentials([script.sshUserPrivateKey(credentialsId: "${context.git.credentialsId}",
                    keyFileVariable: 'key', passphraseVariable: '', usernameVariable: 'git_user')]) {
                script.sh """
                eval `ssh-agent`
                ssh-add ${script.key}
                mkdir -p ~/.ssh
                ssh-keyscan -p ${context.git.sshPort} ${context.git.host} >> ~/.ssh/known_hosts
                git config --global user.email ${context.git.autouser}@epam.com
                git config --global user.name ${context.git.autouser}
                git tag -a ${context.codebase.vcsTag} -m 'Tag is added automatically by \
                     ${context.git.autouser} user'
                git push --tags"""
            }
        }
    }
}

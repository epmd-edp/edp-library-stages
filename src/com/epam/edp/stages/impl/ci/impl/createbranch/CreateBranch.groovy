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

package com.epam.edp.stages.impl.ci.impl.createbranch

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "create-branch", buildTool = ["maven", "npm", "dotnet","gradle"], type = [ProjectType.APPLICATION, ProjectType.AUTOTESTS])
class CreateBranch {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            script.withCredentials([script.sshUserPrivateKey(credentialsId: "${context.gerrit.credentialsId}",
                    keyFileVariable: 'key', passphraseVariable: '', usernameVariable: 'git_user')]) {
                try {
                    script.sh """
                eval `ssh-agent`
                ssh-add ${script.key}
                mkdir -p ~/.ssh
                ssh-keyscan -p ${context.gerrit.sshPort} ${context.gerrit.host} >> ~/.ssh/known_hosts
                git config --global user.email ${context.gerrit.autouser}@epam.com
                git config --global user.name ${context.gerrit.autouser}
                git checkout -b ${context.job.newBranch}
                git push --all
                """
                }
                catch(Exception ex) {
                    script.error "[JENKINS][ERROR] Create branch has failed with excewption - ${ex}"
                }
            }
        }
    }
}

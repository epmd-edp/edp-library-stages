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

package com.epam.edp.stages.impl.ci.impl.helmverify


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "helm-verify", buildTool = ["gradle", "maven", "dotnet", "npm"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class HelmVerifyApplicationLibrary {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            script.sh """
            curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3
            sed -e \'135,140d; s/: \${USE_SUDO:="true"}/: \${USE_SUDO:="false"}/\' get_helm.sh > get_helm_install.sh
            chmod 700 get_helm_install.sh
            ./get_helm_install.sh
            helm version
            """
            }
        }
    }

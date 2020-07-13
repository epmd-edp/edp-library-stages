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

package com.epam.edp.stages.impl.ci.impl.aquamicroscanner


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "aqua-microscanner", buildTool = ["gradle", "maven", "dotnet", "npm"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class AquaMicroscanner {
    Script script

    void run(context) {
            script.aquaMicroscanner imageName: "build-${context.codebase.name}-${context.git.branch.replaceAll("[^\\p{L}\\p{Nd}]+", "-")}-${script.BUILD_NUMBER.toInteger()}"
        }
    }
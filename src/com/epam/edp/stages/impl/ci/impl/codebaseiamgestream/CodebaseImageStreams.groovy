/* Copyright 2020 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License.*/

package com.epam.edp.stages.impl.ci.impl.codebaseiamgestream

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import hudson.FilePath

import java.text.DateFormat
import java.text.SimpleDateFormat

class CodebaseImageStreams {
    Script script
    def context

    CodebaseImageStreams(context, script) {
        this.context = context
        this.script = script
    }

    def UpdateOrCreateCodebaseImageStream(cbisName, repositoryName, imageTag) {
        def crApi = "cbis.${this.context.job.getParameterValue("GIT_SERVER_CR_VERSION")}.edp.epam.com"
        if (!this.context.platform.checkObjectExists(crApi, cbisName)) {
            script.println("[JENKINS][DEBUG] CodebaseImageStream not found. Creating new CodebaseImageStream")

            def res = getCbisTemplate()
            script.println("[JENKINS][DEBUG] res ${res}")

            def template = new JsonSlurperClassic().parseText(res)
            template.metadata.name = cbisName
            template.spec.imageName = repositoryName

            def cbisTemplateFilePath = saveCbisTemplateFile(template)
            this.context.platform.apply(cbisTemplateFilePath.getRemote())
        }

        script.println("[JENKINS][DEBUG] Co1111111debaseImageStream not found. Creating new CodebaseImageStream")

        def qq = this.context.platform.getJsonValue(crApi, cbisName)
        script.println("[JENKINS][DEBUG] Co1111111debaseImageStream not found. Creating new CodebaseImageStream ${qq}")

        def cbisCr = new JsonSlurperClassic().parseText(qq)
        def cbisTags = cbisCr.spec.tags ? cbisCr.spec.tags : []
        if (!cbisTags.find { it.name == imageTag }) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            cbisTags.add(['name': imageTag, 'created': dateFormat.format(new Date())])
            def newCbisTags = JsonOutput.toJson(cbisTags)
            script.sh("kubectl patch --type=merge ${crApi} ${cbisName} -p '{\"spec\":{\"tags\":${newCbisTags}}}'")
        }
    }

    def getCbisTemplate() {
        def template = this.context.platform.getJsonPathValue("cm", "cbis-template", ".data.cbis\\.json")
        if (template == null) {
            script.error("[JENKINS][ERROR] There're no cbis-template in cluster")
        }
        return template
    }

    def saveCbisTemplateFile(template) {
        def cbisTemplateFilePath = new FilePath(Jenkins.getInstance().getComputer(script.env['NODE_NAME']).getChannel(),
                "${this.context.workDir}/cbis-template.json")
        cbisTemplateFilePath.write(JsonOutput.toJson(template), null)
        return cbisTemplateFilePath
    }

}
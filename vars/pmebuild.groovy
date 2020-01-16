/**
 *
 * @param projectCollection the project list to build
 * @param settingsXmlId the maven settings id from jenkins
 * @param buildConfigFile the build config yaml file
 * @param pmeCliPath the pme cli path
 */
def buildProjects(List<String> projectCollection, String settingsXmlId, File buildConfigFile, String pmeCliPath) {
    println "Build projects ${projectCollection}"
    def buildConfig = getBuildConfiguration(buildConfigFile)
    projectCollection.each { project -> buildProject(project, settingsXmlId, buildConfig, pmeCliPath) }
}

/**
 * Builds the project
 * @param project the project name (this should match with the builds.project from the file)
 * @param settingsXmlId the maven settings id from jenkins
 * @param buildConfig the build config map
 * @param pmeCliPath the pme cli path
 * @param defaultGroup the default group in case the project is not defined as group/name
 */
def buildProject(String project, String settingsXmlId, def buildConfig, String pmeCliPath, String defaultGroup = "kiegroup") {
    def projectNameGroup = project.split("\\/")
    def group = projectNameGroup.size() > 1 ? projectNameGroup[0] : defaultGroup
    def name = projectNameGroup.size() > 1 ? projectNameGroup[1] : project
    def finalProjectName = "${group}/${name}"

    println "Building ${finalProjectName}"
    sh "mkdir -p ${group}_${name}"
    sh "cd ${group}_${name}"
    githubscm.checkoutIfExists(name, "$CHANGE_AUTHOR", "$CHANGE_BRANCH", group, "$CHANGE_TARGET")

    executePME("${finalProjectName}", buildConfig, pmeCliPath)
    String goals = getMavenGoals("${finalProjectName}", buildConfig)
    maven.runMavenWithSettings(settingsXmlId, goals, new Properties())
    sh "cd .."
}

/**
 * Parses the build config yaml file to a map
 * @param buildConfigFile the yaml file
 * @return the yaml map
 */
def getBuildConfiguration(File buildConfigFile) {
    def additionalVariables = [datetimeSuffix: new Date().format("yyyyMMdd")]
    treatVariables(buildConfigFile, additionalVariables)
    treatVariables(buildConfigFile, additionalVariables) //TODO: don't know why it's needed twice

    return readYaml(text: buildConfigFile.text)
}

/**
 * Gets the project configuration from the buildConfig map
 * @param project the project name (this should match with the builds.project from the file)
 * @param buildConfig the buildConfig map
 * @return the project configuration
 */
def getProjectConfiguration(String project, def buildConfig) {
    println "BUILD CONFIG ${buildConfig['builds']}"
    return buildConfig['builds'].find { (project == it['project']) }
}

/**
 *
 * @param buildConfigFile
 * @param variableValues you can pass throw something like [productVersion: "1.0", milestone: "CRX"]
 * @return
 */
def treatVariables(File buildConfigFile, def variableValues = null) {
    AntBuilder antBuilder = new AntBuilder()
    Map<String, Object> variables = getFileVariables(buildConfigFile) << (variableValues == null ? [:] : variableValues)
    variables.each { key, value ->
        antBuilder.replace(file: buildConfigFile, token: '{{' + key + '}}', value: value)
    }
}

/**
 * Gets the variables #! and adds them to a map
 * @param buildConfigFile the build config file
 * @return a key:value map with #! variables from the  buildConfigFile
 */
def getFileVariables(File buildConfigFile) {
    def variables = [:]
    def matcher = buildConfigFile.text =~ /(#!)([a-zA-Z0-9_-]*)(=)(.*)/

    matcher.each { value ->
        variables.put(value[2], value[4])
    }
    return variables
}

/**
 * Executes the pme for the project
 * @param project the project name (this should match with the builds.project from the file)
 * @param buildConfig the buildConfig map
 * @param pmeCliPath the pme cli path
 */
def executePME(String project, def buildConfig, String pmeCliPath) {
    def projectConfig = getProjectConfiguration(project, buildConfig)
    if (projectConfig != null) {
        List<String> customPmeParameters = projectConfig['customPmeParameters']
        println "PME parameters for ${project}: ${customPmeParameters.join(' ')}"
        sh "java -jar ${pmeCliPath} -DversionIncrementalSuffix=redhat -DallowConfigFilePrecedence=true -DprojectSrcSkip=false -DversionIncrementalSuffixPadding=5 -DversionSuffixStrip= ${customPmeParameters.join(' ')}"
    }
}

/**
 * Gets the goal for the project from the buildConfig map
 * @param project
 * @param buildConfig
 * @return the goal for the project
 */
def getMavenGoals(String project, def buildConfig) {
    def Map<String, Object> projectConfig = getProjectConfiguration(project, buildConfig)
    return (projectConfig != null && projectConfig['buildScript'] != null ? projectConfig['buildScript'] : buildConfig['defaultBuildParameters']['buildScript']).minus("mvn ")
}

return this;

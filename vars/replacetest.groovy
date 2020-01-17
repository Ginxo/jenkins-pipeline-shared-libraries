import org.yaml.snakeyaml.Yaml

/**
 *
 *
 * @param projectCollection the project list to build
 * @param settingsXmlId the maven settings id from jenkins
 * @param buildConfigContent the build config yaml content
 * @param pmeCliPath the pme cli path
 */
def buildProjects(List<String> projectCollection, String settingsXmlId, String buildConfigContent, String pmeCliPath) {
    println "Build projects ${projectCollection}"
    Map<String, Object> buildConfigMap = getBuildConfiguration(buildConfigContent)
    projectCollection.each { project -> buildProject(project, settingsXmlId, buildConfigMap, pmeCliPath) }
}

/**
 * Builds the project
 * @param project the project name (this should match with the builds.project from the file)
 * @param settingsXmlId the maven settings id from jenkins
 * @param buildConfig the build config map
 * @param pmeCliPath the pme cli path
 * @param defaultGroup the default group in case the project is not defined as group/name
 */
def buildProject(String project, String settingsXmlId, Map<String, Object> buildConfig, String pmeCliPath, String defaultGroup = "kiegroup") {
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
 * @param buildConfigContent the yaml file content
 * @return the yaml map
 */
def getBuildConfiguration(String buildConfigContent) {
    def additionalVariables = [datetimeSuffix: new Date().format("yyyyMMdd")]
    treatVariables(buildConfigContent, additionalVariables)
    treatVariables(buildConfigContent, additionalVariables)
    Yaml parser = new Yaml()
    return parser.load(buildConfigContent)
}

/**
 * Gets the project configuration from the buildConfig map
 * @param project the project name (this should match with the builds.project from the file)
 * @param buildConfig the buildConfig map
 * @return the project configuration
 */
def getProjectConfiguration(String project, Map<String, Object> buildConfig) {
    return buildConfig['builds'].find { (project == it['project']) }
}

/**
 *
 * @param buildConfigContent
 * @param variableValues you can pass throw something like [productVersion: "1.0", milestone: "CRX"]
 * @return
 */
def treatVariables(String buildConfigContent, Map<String, Object> variableValues = null) {
    AntBuilder antBuilder = new AntBuilder()
    Map<String, Object> variables = getFileVariables(buildConfigContent) << (variableValues == null ? [:] : variableValues)

    variables.each { key, value ->
        buildConfigContent = buildConfigContent.replaceAll('\\{{')
        antBuilder.replace(file: buildConfig, token: '{{' + key + '}}', value: value) // TODO
    }
}

/**
 * Gets the variables #! and adds them to a map
 * @param buildConfigContent the build config file content
 * @return a key:value map with #! variables from the  buildConfigFile
 */
def getFileVariables(String buildConfigContent) {
    def variables = [:]
    def matcher = buildConfigContent =~ /(#!)([a-zA-Z0-9_-]*)(=)(.*)/

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
def executePME(String project, Map<String, Object> buildConfig, String pmeCliPath) {
    def projectConfig = getProjectConfiguration(project, buildConfig)
    if (projectConfig != null) {
        List<String> customPmeParameters = projectConfig['customPmeParameters']
        println "PME parameters for ${project}: ${customPmeParameters.join(' ')}"
        // TODO: pending pme flags
        sh "java -jar ${pmeCliPath} -DversionIncrementalSuffix=redhat -DallowConfigFilePrecedence=true -DprojectSrcSkip=false -DversionIncrementalSuffixPadding=5 -DversionSuffixStrip= ${customPmeParameters.join(' ')}"
    }
}

/**
 * Gets the goal for the project from the buildConfig map
 * @param project
 * @param buildConfig
 * @return the goal for the project
 */
def getMavenGoals(String project, Map<String, Object> buildConfig) {
    def Map<String, Object> projectConfig = getProjectConfiguration(project, buildConfig)
    return (projectConfig != null && projectConfig['buildScript'] != null ? projectConfig['buildScript'] : buildConfig['defaultBuildParameters']['buildScript']).minus("mvn ")
}

return this;

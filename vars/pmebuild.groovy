import org.yaml.snakeyaml.Yaml

/**
 *
 *
 * @param projectCollection the project list to build
 * @param settingsXmlId the maven settings id from jenkins
 * @param buildConfigPathFolder the build config folder where groovy and yaml files are contained
 * @param pmeCliPath the pme cli path
 */
def buildProjects(List<String> projectCollection, String settingsXmlId, String buildConfigPathFolder, String pmeCliPath, String deploymentRepoUrl) {
    println "Build projects ${projectCollection}. Build path ${buildConfigPathFolder}"
    def buildConfigContent = readFile "${buildConfigPathFolder}/build-config.yaml"
    Map<String, Object> buildConfigMap = getBuildConfiguration(buildConfigContent, buildConfigPathFolder)
    projectCollection.each { project -> buildProject(project, settingsXmlId, buildConfigMap, pmeCliPath, deploymentRepoUrl) }
}

/**
 * Builds the project
 * @param project the project name (this should match with the builds.project from the file)
 * @param settingsXmlId the maven settings id from jenkins
 * @param buildConfig the build config map
 * @param pmeCliPath the pme cli path
 * @param defaultGroup the default group in case the project is not defined as group/name
 */
def buildProject(String project, String settingsXmlId, Map<String, Object> buildConfig, String pmeCliPath, String deploymentRepoUrl, String defaultGroup = "kiegroup") {
    def projectNameGroup = project.split("\\/")
    def group = projectNameGroup.size() > 1 ? projectNameGroup[0] : defaultGroup
    def name = projectNameGroup.size() > 1 ? projectNameGroup[1] : project
    def finalProjectName = "${group}/${name}"

    println "Building ${finalProjectName}"
    sh "mkdir -p ${group}_${name}"
    dir("${env.WORKSPACE}/${group}_${name}") {
        githubscm.checkoutIfExists(name, "$CHANGE_AUTHOR", "$CHANGE_BRANCH", group, "$CHANGE_TARGET")

        executePME("${finalProjectName}", buildConfig, pmeCliPath)
        String goals = getMavenGoals("${finalProjectName}", buildConfig)
        
        maven.runMavenWithSettings(settingsXmlId, "${goals} -DrepositoryId=indy -DaltDeploymentRepository=indy::default::${deploymentRepoUrl}", new Properties())
    }
}

/**
 * Parses the build config yaml file to a map
 * @param buildConfigContent the yaml file content
 * @return the yaml map
 */
def getBuildConfiguration(String buildConfigContent, String buildConfigPathFolder) {
    def additionalVariables = [datetimeSuffix: new Date().format("yyyyMMdd"), groovyScriptsPath: "file://${buildConfigPathFolder}"]
    Map<String, Object> variables = getFileVariables(buildConfigContent) << additionalVariables

    def buildConfigContentTreated = treatVariables(buildConfigContent, variables)

    Yaml parser = new Yaml()
    return parser.load(buildConfigContentTreated)
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
 * @param variables you can pass throw something like [productVersion: "1.0", milestone: "CRX"]
 * @return
 */
def treatVariables(String buildConfigContent, Map<String, Object> variables) {
    def content = buildConfigContent
    variables.each { key, value ->
        content = content.replaceAll('\\{\\{' + key + '}}', value)
    }
    def matcher = content =~ /\{\{[a-zA-Z_0-9]*\}\}/

    return matcher.find() ? treatVariables(content, variables) : content
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
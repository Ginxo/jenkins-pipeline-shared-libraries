/**
 * Builds the downstream
 * @param projectCollection a collection of items following the pattern PROJECT_GROUP/PROJECT_NAME, for example kiegroup/drools
 */
def downstreamBuild(def projectCollection) {
    def lastLine = projectCollection.get(projectCollection.size() - 1)

    println "Downstream building ${lastLine} project"
    upstreamBuild(projectCollection, lastLine)
}

/**
 * Builds the upstream for an specific project
 * @param projectCollection a collection of items following the pattern PROJECT_GROUP/PROJECT_NAME, for example kiegroup/drools
 * @param currentProject the project to build the stream from, like kiegroup/drools
 */
def upstreamBuild(def projectCollection, String currentProject) {
    println "Upstream building ${currentProject} project"

    // Build project tree from currentProject node
    for (i = 0; currentProject != projectCollection.get(i); i++) {
        buildProject(projectCollection.get(i))
    }

    buildProject(currentProject)
}

/**
 *
 * @param project a string following the pattern PROJECT_GROUP/PROJECT_NAME, for example kiegroup/drools
 */
def buildProject(String project) {
    def projectGroup = project.split("\\/")[0]
    def projectName = project.split("\\/")[1]
    println "Building ${projectGroup}/${projectName}"
    sh "mkdir -p ${projectGroup}_${projectName}"
    sh "cd ${projectGroup}_${projectName}"
    githubscm.checkoutIfExists(projectName, "$CHANGE_AUTHOR", "$CHANGE_BRANCH", projectGroup, "$CHANGE_TARGET")
    maven.runMavenWithSettings('771ff52a-a8b4-40e6-9b22-d54c7314aa1e', 'clean install', true)
    sh "cd .."
}

/**
 *
 * @param projectUrl the github project url
 */
def getProject(String projectUrl) {
    return (projectUrl =~ /((git|ssh|http(s)?)|(git@[\w\.]+))(:(\/\/)?(github.com\\/))([\w\.@\:\/\-~]+)(\.git)(\/)?/)[0][8]
}

return this;

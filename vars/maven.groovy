import java.util.Properties

def runMavenWithSettings(String settingsXmlId, String goals, Properties properties) {
    configFileProvider([configFile(fileId: settingsXmlId, variable: 'MAVEN_SETTINGS_XML')]) {
        def propertiesString = ''

        properties.each { key, value ->
            propertiesString += "-D$key=$value "
        }

        def mvnCommand = 'mvn -B -s $MAVEN_SETTINGS_XML -fae ' + goals + " $propertiesString"

        sh mvnCommand
    }
}

def runMavenWithSettings(String settingsXmlId, String goals, boolean skipTests) {
    Properties properties = new Properties()
    properties.put('skipTests', skipTests)
    runMavenWithSettings(settingsXmlId, goals, properties)
}

def runMavenWithSubmarineSettings(String goals, boolean skipTests) {
    runMavenWithSettings('9239af2e-46e3-4ba3-8dd6-1a814fc8a56d', goals, skipTests)
}

def runMavenWithSubmarineSettings(String goals, Properties properties) {
    runMavenWithSettings('9239af2e-46e3-4ba3-8dd6-1a814fc8a56d', goals, properties)
}

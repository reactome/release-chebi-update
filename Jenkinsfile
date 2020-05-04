import groovy.json.JsonSlurper
// This Jenkinsfile is used by Jenkins to run the 'ChEBIUpdate' step of Reactome's release.
// This step synchronizes Reactome's GO terms with Gene Ontology. 
// It requires that the 'GOUpdate' step has been run successfully before it can be run.
def currentRelease
def previousRelease
pipeline {
	agent any

	stages {
		// This stage checks that an upstream step, ConfirmReleaseConfigs, was run successfully.
		stage('Check GOUpdate build succeeded'){
			steps{
				script{
					// Get current release number from directory
					currentRelease = (pwd() =~ /(\d+)\//)[0][1];
					previousRelease = (pwd() =~ /(\d+)\//)[0][1].toInteger() - 1;
					// This queries the Jenkins API to confirm that the most recent build of 'GOUpdate' was successful.
					def goStatusUrl = httpRequest authentication: 'jenkinsKey', validResponseCodes: "${env.VALID_RESPONSE_CODES}", url: "${env.JENKINS_JOB_URL}/job/${currentRelease}/job/Pre-Slice/job/GOUpdate/lastBuild/api/json"
					if (goStatusUrl.getStatus() == 404) {
						error("GOUpdate has not yet been run. Please complete a successful build.")
					} else {
						def goStatusJson = new JsonSlurper().parseText(goStatusUrl.getContent())
						if (goStatusJson['result'] != "SUCCESS"){
							error("Most recent GOUpdate build status: " + goStatusJson['result'] + ". Please complete a successful build.")
						}
					}
				}
			}
		}

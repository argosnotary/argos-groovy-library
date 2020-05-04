@Grab('io.github.http-builder-ng:http-builder-ng-core:1.0.4')

import static groovyx.net.http.HttpBuilder.configure
import static groovyx.net.http.ContentTypes.JSON
import groovyx.net.http.*

@NonCPS
def call(String deployitManifestLocation, String xldeployUrl, String usr, String pswd) {

  def versionName
  def applicationName
  def result

  File file = new File(deployitManifestLocation)
  def xml = new XmlSlurper().parseText(file.text)
  applicationName = xml.@application.toString()
  versionName = xml.@version.toString()

  def httpConfig = configure {
	  request.uri = xldeployUrl
	  request.accept = JSON[0]
	  request.contentType = JSON[0]
	  String encodedAuthString = "Basic " + ((usr+":"+pswd).bytes.encodeBase64().toString())
	  request.headers['authorization'] = encodedAuthString
	}
	
	if (!applicationName.contains("/")) {
	  println applicationName
		result = httpConfig.get() {
		  request.uri.path = '/deployit/repository/query'
		  request.uri.query = [type: 'udm.Application', namePattern: applicationName]
	  }
	  versionId = result.ref+"/"+versionName
	} else {
		versionId = "Applications/"+applicationName+"/"+versionName
	}

  println 'Create Link object for Artifacts for XLDeploy Applicication ['+versionId+']'

	// get handle to task
	result = httpConfig.get() {
	  request.uri.path = '/deployit/control/prepare/collectArgosLink/'+versionId
  }

	// create task
	result = httpConfig.post() {
	  request.uri.path = '/deployit/control'
	  request.body = result
	}

	def taskId = result.string
	
	// start task
	result = httpConfig.post() {
	  request.uri.path = '/deployit/tasks/v2/'+taskId+'/start'
	  request.body = result
	}

	state = ""
	secondsWaited = 0
	while (state != "EXECUTED" && secondsWaited < 180) {
		sleep(1000)
		secondsWaited += 20
	  result = httpConfig.get() {
			request.uri.path = '/deployit/tasks/v2/'+taskId
		}
	  state = result.state
  }
  
  if (state != "EXECUTED" && result.failers != 0) {
	  throw new RuntimeException("XLD collect task failed")
  } else {
		// archive task
		result = httpConfig.post() {
		  request.uri.path = '/deployit/tasks/v2/'+taskId+'/archive'
		  request.body = result
		}
	  println 'Link object created'
	}
 
}

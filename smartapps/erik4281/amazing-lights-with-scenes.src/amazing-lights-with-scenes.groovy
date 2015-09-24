/**
 *  Amazing Lights
 *
 *  Copyright 2015 Erik Vennink
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

/************
 * Metadata *
 ************/

definition(
    name: "Amazing Lights with scenes",
    namespace: "erik4281",
    author: "Erik Vennink",
    description: "Lights are completely automated with this SmartApp. Activated by motion, open-close switch or a normal (virtual) switch. Different scenes can be programmed, depending on day of week, time or mode.",
    category: "SmartThings Labs",
    iconUrl: "http://icons.iconarchive.com/icons/hopstarter/sleek-xp-software/128/Nero-Smart-Start-icon.png",
    iconX2Url: "http://icons.iconarchive.com/icons/hopstarter/sleek-xp-software/128/Nero-Smart-Start-icon.png",
    iconX3Url: "http://icons.iconarchive.com/icons/hopstarter/sleek-xp-software/128/Nero-Smart-Start-icon.png")


/**********
 * Setup  *
 **********/

preferences {
	page(name: "switchPage", title: "Switch on lights when this happens:", nextPage: "scenesPage", uninstall: true) 
	page(name: "scenesPage", title: "Switch these lights when this is true:", install: true, uninstall: true) 
	page(name: "optionsPage", title: "Use these options:", install: false, uninstall: false, previousPage: "scenesPage") 
	page(name: "devicePage", install: false, uninstall: false, previousPage: "optionsPage")
}

def switchPage() {
	dynamicPage(name: "switchPage") {
		section("Control these switches") {
			input "lights", "capability.switchLevel", multiple: true, required: false, title: "Lights, switches & dimmers"
		}
		section("Switch on when..."){
			input "motionSensor", "capability.motionSensor", title: "Motion here", required: false, multiple: true
			input "contactSensor", "capability.contactSensor", title: "Contact opens", required: false, multiple: true
			input "inputSwitch", "capability.switch", title: "Switch turns on", required: false, multiple: true
			input "triggerModes", "mode", title: "System changes mode", required: false, multiple: true
			input "timeOfDay", "time", title: "At a scheduled time", required: false
		}
		section("Switch off when..."){
			input "delayMinutes", "number", title: "Off after x minutes of motion/contact", required: false
			input "triggerModesOff", "mode", title: "System changes mode", required: false, multiple: true
			input "timeOfDayOff", "time", title: "At a scheduled time", required: false
		}
		section("Or switch off faster") {
			input "shortDelayMinutes", "number", title: "Off after x minutes", required: false
			input "shortModes", "mode", title: "In mode(s)", required: false, multiple: true
			input "shortStarting", "time", title: "And starting from", required: false
			input "shortEnding", "time", title: "And ending at", required: false
		}
		section("Monitor illuminance sensor") {
			input "lightSensor", "capability.illuminanceMeasurement", title: "Sensor(s)?", required: false
			input "lightOnValue", "number", title: "On at < (Lux, empty = 100)?", required: false
			input "lightOffValue", "number", title: "Off at > (Lux, empty = 150)?", required: false
		}
		section("Use MoodCube switch (disable auto-switching light)") {
			input "moodSwitch", "capability.switch", title: "Switch", required: false
		}
		section([mobileOnly:true]) {
			label title: "Assign a name", required: false
		}
	}
}

def scenesPage() {
	def sceneId = getTiming()
	dynamicPage(name: "scenesPage") {
		section {
			for (num in 1..6) {
				href "optionsPage", title: "${num}. ${sceneName(num)}${sceneId==num ? ' (current)' : ''}", params: [sceneId:num], description: "", state: sceneIsDefined(num) ? "complete" : "incomplete"
			}
		}
	}
}

def optionsPage(params=[:]) {
	log.debug "optionsPage($params)"
	def sceneId = params.sceneId as Integer ?: state.lastDisplayedSceneId
	state.lastDisplayedSceneId = sceneId
	dynamicPage(name:"optionsPage", title: "${sceneId}. ${sceneName(sceneId)}") {
		section {
			input "sceneName${sceneId}", "text", title: "Scene Name", required: false
		}
		section {
			href "devicePage", title: "Show Device States", params: [sceneId:sceneId], description: "", state: sceneIsDefined(sceneId) ? "complete" : "incomplete"
		}
	}
}

def devicePage(params) {
	getDeviceCapabilities()
	def sceneId = params.sceneId as Integer ?: state.lastDisplayedSceneId
	dynamicPage(name: "devicePage") {
		section {
			input "sceneName${sceneId}", "text", title: "Scene Name", required: false
		}
		section("Lights") {
			lights.each {light ->
				input "onoff_${sceneId}_${light.id}", "boolean", title: light.displayName
			}
		}
		section("Dimmers") {
			lights.each {light ->
				input "level_${sceneId}_${light.id}", "enum", title: light.displayName, options: levels, description: "", required: false
			}
		}
		section("Colors") {
			lights.each {light ->
				input "color_${sceneId}_${light.id}", "enum", title: light.displayName, required: false, multiple:false, options: [
					["Soft White":"Soft White - Default"],
					["White":"White - Concentrate"],
					["Daylight":"Daylight - Energize"],
					["Warm White":"Warm White - Relax"],
					"Red","Green","Blue","Yellow","Orange","Purple","Pink"]
			}
		}
		section("Timing options") {
			input "starting_${sceneId}", "time", title: "Starting from", required: false
			input "ending_${sceneId}", "time", title: "Ending at", required: false
			input "days_${sceneId}", "enum", title: "Only on certain days of the week", multiple: true, required: false,
				options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
			input "modes_${sceneId}", "mode", title: "Only when mode is", multiple: true, required: false
		}
	}
}

/*************************
 * Installation & update *
 *************************/

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
	subscribe(app, appTouchHandler)
	subscribe(motionSensor, "motion.active", eventHandler)
	subscribe(contactSensor, "contact.open", eventHandler)
	subscribe(inputSwitch, "switch.on", eventHandler)
	subscribe(motionSensor, "motion.inactive", eventOffHandler)
	subscribe(contactSensor, "contact.close", eventOffHandler)
	subscribe(inputSwitch, "switch.off", eventOffHandler)
	if (lightSensor) {
		subscribe(lightSensor, "illuminance", illuminanceHandler, [filterEvents: false])
	}
	if (triggerModes || triggerModesOff) {
		subscribe(location, modeChangeHandler)
	}
	if (timeOfDay) {
		schedule(timeOfDay, scheduledTimeHandler)
	}
	if (timeOfDayOff) {
		schedule(timeOfDayOff, scheduledTimeOffHandler)
	}
}

/******************
 * Event handlers *
 ******************/

def appTouchHandler(evt) {

	def sceneId = getTiming()
	if (sceneId != state.lastActiveSceneId) {
		activateHue()
	}
    state.lastActiveSceneId = sceneId


}

def eventHandler(evt) {
    state.eventStopTime = null
	if (modeOk && daysOk && timeOk) {
		if (darkOk && switchOk && moodOk) {
        	activateHue()
		}
	}
}

def eventOffHandler(evt) {
	state.eventStopTime = now()
	if (switchOk && modeOk && daysOk && timeOk) {
		if ((shortModeOk || shortTimeOk) && shortDelayMinutes) {
        	runIn(shortDelayMinutes*60, turnOffAfterDelayShort, [overwrite: false])
                }
        }
        else if (delayMinutes) {
        	runIn(delayMinutes*60, turnOffAfterDelay, [overwrite: false])
        }
		else  {
        	turnOffAfterDelay()
		}
    //else if (switchOk) {
    //	runIn(60*60, turnOffAfterDelay, [overwrite: false])
    //}
}

def illuminanceHandler(evt) {
    if (modeOk && daysOk && timeOk) {
        if (state.lastStatus != "off" && evt.integerValue > (lightOffValue ?: 150)) {
            deactivateHue()
        }
        else if (state.eventStopTime) {
            if (state.lastStatus != "off" && switchOk) {
                def elapsed = now() - state.eventStopTime                
			    if((shortModeOk || shortTimeOk) && shortDelayMinutes) {
                   if (elapsed >= ((shortDelayMinutes ?: 0) * 60000L) - 2000) {
			        	deactivateHue()
                    }
			    }
				else if(delayMinutes) {
                    if (elapsed >= ((delayMinutes ?: 0) * 60000L) - 2000) {
                    	deactivateHue()
                    }
                }
            }
            else if (state.lastStatus != "on" && evt.integerValue < (lightOnValue ?: 100) && switchOk != true) {
                activateHue()
            }
        }
        else if (state.lastStatus != "on" && evt.integerValue < (lightOnValue ?: 100)){
            activateHue()
        }
	}
}

def modeChangeHandler(evt) {
	if (evt.value in triggerModes) {
		pause(2000)
		activateHue()
	}
	if (evt.value in triggerModesOff) {
		pause(2000)
        deactivateHue()
	}
}

def scheduledTimeHandler() {
	pause(2000)
    activateHue()
}

def scheduledTimeOffHandler() {
	pause(2000)
    deactivateHue()
}

/********************
 * Actuator methods *
 ********************/

def turnOffAfterDelay() {
    if (state.eventStopTime && state.lastStatus != "off") {
		def elapsed = now() - state.eventStopTime
        if (elapsed >= ((delayMinutes ?: 0) * 60000L) - 2000) {
        	deactivateHue()
		}
	}
}

def turnOffAfterDelayShort() {
    if (state.eventStopTime && state.lastStatus != "off") {
		def elapsed = now() - state.eventStopTime
        if (elapsed >= ((shortDelayMinutes ?: 0) * 60000L) - 2000) {
        	deactivateHue()
		}
	}
}

private activateHue() {
	state.lastStatus = "on"
	getDeviceCapabilities()
	lights.each {light ->
		def type = state.lightCapabilities[light.id]
		def isOn = settings."onoff_${sceneId}_${light.id}" == "true" ? true : false
		if (type != "switch" && moodOk) {
			def level = switchLevel(light)
			if (type == "level") {
				if (level != null) {
					light.setLevel(level)
				}
			}
			else if (type == "color") {
				def hue = 23
				def saturation = 56
				switch(settings."color_${sceneId}_${light.id}") {
					case "White":
						hue = 52
						saturation = 19
						break;
					case "Daylight":
						hue = 53
						saturation = 91
						break;
					case "Soft White":
						hue = 23
						saturation = 56
						break;
					case "Warm White":
						hue = 20
						saturation = 80
						break;
					case "Blue":
						hue = 70
			            saturation = 100
						break;
					case "Green":
						hue = 39
			            saturation = 100
						break;
					case "Yellow":
						hue = 25
			            saturation = 100
						break;
					case "Orange":
						hue = 10
			            saturation = 100
						break;
					case "Purple":
						hue = 75
			            saturation = 100
						break;
					case "Pink":
						hue = 83
			            saturation = 100
						break;
					case "Red":
						hue = 100
			            saturation = 100
						break;
				}
				if (level != null) {
					light.setColor(level: level, hue: hue, saturation: saturation)
				}
				else {
					light.setColor(hue: hue, saturation: saturation)
				}
			}
			else {
				if (level != null) {
					light.setLevel(level)
				}
			}
		}	
		else {
		}		
		if (isOn) {
			light.on()
		}
		else {
			light.off()
		}
	}
}

private deactivateHue() {
	state.lastStatus = "off"
    lights.each {light ->
        light.off()
    }
}

/******************
 * Helper methods *
 ******************/

private getTiming() {

	//if 


	//def result = !modes || modes.contains(location.mode)
	//result

	//def result = true
	//if (days) {
	//	def df = new java.text.SimpleDateFormat("EEEE")
	//	if (location.timeZone) {
	//		df.setTimeZone(location.timeZone)
	//	}
	//	else {
	//		df.setTimeZone(TimeZone.getTimeZone("America/New_York"))
	//	}
	//	def day = df.format(new Date())
	//	result = days.contains(day)
	//}
	//result

	//def result = true
	//if (starting && ending) {
	//	def currTime = now()
	//	def start = timeToday(starting, location?.timeZone).time
	//	def stop = timeToday(ending, location?.timeZone).time
	//	result = start < stop ? currTime >= start && currTime <= stop : currTime <= stop || currTime >= start
	//}
	//result








	//final threshold = 250
	//def value = xyz ?: cube.currentValue("threeAxis")
	//def x = Math.abs(value.x) > threshold ? (value.x > 0 ? 1 : -1) : 0
	//def y = Math.abs(value.y) > threshold ? (value.y > 0 ? 1 : -1) : 0
	//def z = Math.abs(value.z) > threshold ? (value.z > 0 ? 1 : -1) : 0
	//def orientation = 6
	//if (z > 0) {
	//	if (x == 0 && y == 0) {
	//		orientation = 1
	//	}
	//}
	//else if (z < 0) {
	//	if (x == 0 && y == 0) {
	//		orientation = 2
	//	}
	//}
	//else {
	//	if (x > 0) {
	//		if (y == 0) {
	//			orientation = 3
	//		}
	//	}
	//	else if (x < 0) {
	//		if (y == 0) {
	//			orientation = 4
	//		}
	//	}
	//	else {
	//		if (y > 0) {
	//			orientation = 5
	//		}
	//		else if (y < 0) {
	//			orientation = 6
	//		}
	//	}
	//}
	//orientation
}

private closestLevel(level) {
	level ? "${Math.round(level/5) * 5}%" : "0%"
}

private switchLevel(light) {
	def percent = settings."level_${sceneId}_${light.id}"
	if (percent) {
		percent[0..-2].toInteger()
	}
	else {
		null
	}
}

private getDeviceCapabilities() {
	def caps = [:]
	lights.each {
		if (it.hasCapability("Color Control")) {
            caps[it.id] = "color"
		}
		else if (it.hasCapability("Switch Level")) {
            caps[it.id] = "level"
		}
		else {
            caps[it.id] = "switch"
		}
	}
	state.lightCapabilities = caps
}

private getLevels() {
	def levels = []
	for (int i = 0; i <= 100; i += 10) {
		levels << "$i%"
	}
	levels
}

private Boolean sceneIsDefined(sceneId) {
	def tgt = "onoff_${sceneId}".toString()
	settings.find{it.key.startsWith(tgt)} != null
}

private sceneName(num) {
	final names = ["UNDEFINED","One","Two","Three","Four","Five","Six"]
	settings."sceneName${num}" ?: "Scene ${names[num]}"
}

/***********
 * Checks  *
 ***********/

private getSwitchOk() {
	def result = true
	if (inputSwitch) {
    	def current = inputSwitch.currentValue('switch')
		def switchValue = inputSwitch.find{it.currentSwitch == "on"}
		if (switchValue) {
    		result = false
        }
        else {
        	result = true
        }
    }
    else {
    	result = true
    }
    result
}

private getMoodOk() {
	if (moodSwitch) {
    	if (moodSwitch.currentSwitch == "off") {
			def result = true
    		result
    	}
        else {
        	def result = false
            result
    	}
    }
    else {
    	def result = true
    	result
	}
}

private getDarkOk() {
	def result = true
	if (lightSensor) {
		result = lightSensor.currentIlluminance < (lightOnValue ?: 100)
	}
	else {
		result = true
	}
	result
}

private getShortModeOk() {
	def result = !shortModes || shortModes.contains(location.mode)
	log.trace "shortModeOk = $result"
	result
}

private getShortTimeOk() {
	def result = false
	if (shortStarting && shortEnding) {
		def currTime = now()
		def shortStart = timeToday(shortStarting).time
		def shortStop = timeToday(shortEnding).time
		result = shortStart < shortStop ? currTime >= shortStart && currTime <= shortStop : currTime <= shortStop || currTime >= shortStart
	}
	log.trace "shortTimeOk = $result"
	result
}

private getModeOk() {
	def result = !modes || modes.contains(location.mode)
	result
}

private getDaysOk() {
	def result = true
	if (days) {
		def df = new java.text.SimpleDateFormat("EEEE")
		if (location.timeZone) {
			df.setTimeZone(location.timeZone)
		}
		else {
			df.setTimeZone(TimeZone.getTimeZone("America/New_York"))
		}
		def day = df.format(new Date())
		result = days.contains(day)
	}
	result
}

private getTimeOk() {
	def result = true
	if (starting && ending) {
		def currTime = now()
		def start = timeToday(starting, location?.timeZone).time
		def stop = timeToday(ending, location?.timeZone).time
		result = start < stop ? currTime >= start && currTime <= stop : currTime <= stop || currTime >= start
	}
	result
}

//private hhmm(time, fmt = "h:mm a")
//{
//	def t = timeToday(time, location.timeZone)
//	def f = new java.text.SimpleDateFormat(fmt)
//	f.setTimeZone(location.timeZone ?: timeZone(time))
//	f.format(t)
//}
//
//private timeIntervalLabel()
//{
//	(starting && ending) ? hhmm(starting) + "-" + hhmm(ending, "h:mm a z") : ""
//}

/**
 *  Hue SceneMaker
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
    name: "Hue SceneMaker",
    namespace: "erik4281",
    author: "Erik Vennink",
    description: "Switch on and set Hue lights based on an (virtual) switch.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

/**********
 * Setup  *
 **********/

preferences {
    page(name: "lightSelectPage", title: "Turn on these lights:", nextPage: "optionsPage", params: [sceneId:sceneId], uninstall: true) 
    page(name: "optionsPage", title: "Use these options:", install: true, uninstall: true) 
}

def lightSelectPage() {
	dynamicPage(name: "lightSelectPage") {
        section("Use this (virtual) switch"){
            input "inputSwitch", "capability.switch", title: "Switches", required: true, multiple: true
        }
		section("To control these lights") {
			input "lights", "capability.switchLevel", multiple: true, required: false, title: "Lights, switches & dimmers"
		}
		section("Timing options") {
			input "starting", "time", title: "Starting from (also change to this setting when already on...)", required: false
			input "ending", "time", title: "Ending at", required: false
			input "days", "enum", title: "Only on certain days of the week", multiple: true, required: false,
				options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
			input "modes", "mode", title: "Only when mode is", multiple: true, required: false
		}
        section("Lights will also change to these when the mode changes to the selected modes. This only happens when the input switch is enabled!")
		section("Use MoodCube switch (disable auto-switching light at set times and modes when MoodCube is used)") {
			input "moodSwitch", "capability.switch", title: "Switch", required: false
		}
		section([mobileOnly:true]) {
			label title: "Assign a name", required: false
		}
    }
}

def optionsPage(params) {
	dynamicPage(name: "optionsPage") {
		section("Lights") {
			lights.each {light ->
				input "onoff_${light.id}", "boolean", title: light.displayName
			}
		}
		section("Dimmers") {
			lights.each {light ->
				input "level_${light.id}", "enum", title: light.displayName, options: levels, description: "", required: false
			}
		}
		section("Colors") {
			lights.each {light ->
				input "color_${light.id}", "enum", title: light.displayName, required: false, multiple:false, options: [
					["Soft White":"Soft White - Default"],
					["White":"White - Concentrate"],
					["Daylight":"Daylight - Energize"],
					["Warm White":"Warm White - Relax"],
					"Red","Green","Blue","Yellow","Orange","Purple","Pink"]
			}
		}
	}
}

/*************************
 * Installation & update *
 *************************/

def installed() {
	log.debug "Installed with settings: ${settings}"
	subscribeToEvents()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	unschedule()
	subscribeToEvents()
}

def subscribeToEvents() {
	subscribe(app, appTouchHandler)
	subscribe(inputSwitch, "switch", switchHandler)
	if (starting) {
    	schedule(starting, scheduledTimeHandler)
    }
    if (modes) {
		subscribe(location, modeChangeHandler)
	}
}

/******************
 * Event handlers *
 ******************/

def appTouchHandler(evt) {
	log.info "app started manually"
    activateHue()
}

def switchHandler(evt) {
	log.trace "switchHandler()"
	def current = inputSwitch.currentValue('switch')
	def switchValue = inputSwitch.find{it.currentSwitch == "on"}
	if (switchValue && allOk) {
        activateHue()
	}
	else if (switchValue) {
    	log.info "Wrong mode to activate anything"
    }
	else {
        log.info "Nothing to do..."
	}
}

def scheduledTimeHandler() {
	log.trace "scheduledTimeHandler()"
	def current = inputSwitch.currentValue('switch')
	def switchValue = inputSwitch.find{it.currentSwitch == "on"}
	if (switchValue && allOk) {
    	log.trace "do it!"
        activateHue()
	}
    else if (switchValue) {
    	log.info "Wrong mode to activate anything"
	}
    else {
    	log.info "Nothing to do..."
	}
}

def modeChangeHandler(evt) {
	log.trace "modeChangeHandler()"
	def current = inputSwitch.currentValue('switch')
	def switchValue = inputSwitch.find{it.currentSwitch == "on"}
	if (switchValue && allOk) {
    	log.trace "do it!"
        activateHue()
	}
    else if (switchValue) {
    	log.info "Wrong mode to activate anything"
	}
    else {
    	log.info "Nothing to do..."
	}
}

/******************
 * Helper methods *
 ******************/

private closestLevel(level) {
	level ? "${Math.round(level/5) * 5}%" : "0%"
}

private activateHue() {
	log.trace "Activating!"
	state.lastStatus = "on"
	getDeviceCapabilities()
	lights.each {light ->
		def type = state.lightCapabilities[light.id]
		def isOn = settings."onoff_${light.id}" == "true" ? true : false
		log.debug "${light.displayName} is '$isOn'"
		if (isOn) {
			light.on()
		}
		else {
			light.off()
		}
		if (type != "switch" && moodOk) {
			def level = switchLevel(light)
			if (type == "level") {
				log.debug "${light.displayName} level is '$level'"
				if (level != null) {
					light.setLevel(level)
				}
			}
			else if (type == "color") {
				def hue = 23
				def saturation = 56
                log.info settings."color_${light.id}"
				switch(settings."color_${light.id}") {
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
						saturation = 80 //83
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
				log.debug "${light.displayName} color is level: $level, hue: $hue, sat: $saturation"
				if (level != null) {
					light.setColor(level: level, hue: hue, saturation: saturation)
				}
				else {
					light.setColor(hue: hue, saturation: saturation)
				}
			}
			else {
				log.debug "${light.displayName} level is '$level'"
				if (level != null) {
					light.setLevel(level)
				}
			}
		}	
		else {
			log.error "Unknown type '$type'"
		}		
	}
}

private switchLevel(light) {
	def percent = settings."level_${light.id}"
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
			log.debug "colorlight"
            caps[it.id] = "color"
		}
		else if (it.hasCapability("Switch Level")) {
			log.debug "levellight"
            caps[it.id] = "level"
		}
		else {
			log.debug "switchlight"
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

private dayString(Date date) {
	def df = new java.text.SimpleDateFormat("yyyy-MM-dd")
	if (location.timeZone) {
		df.setTimeZone(location.timeZone)
	}
	else {
		df.setTimeZone(TimeZone.getTimeZone("America/New_York"))
	}
	df.format(date)
}

private getMoodOk() {
	if (moodSwitch) {
    	if (moodSwitch.currentSwitch == "off") {
			def result = true
        	log.trace "Switch is available and off: moodOK = ${result}"
    		result
    	}
        else {
        	def result = false
            log.trace "Switch is available and on: moodOK = ${result}"
            result
    	}
    }
    else {
    	def result = true
        log.trace "Switch is NOT available: moodOk = ${result}"
    	result
	}
}

private getAllOk() {
	modeOk && daysOk && timeOk
}

private getModeOk() {
	def result = !modes || modes.contains(location.mode)
	log.trace "modeOk = $result"
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
	log.trace "daysOk = $result"
	result
}

private getTimeOk() {
	def result = true
	if (starting && ending) {
		def currTime = now()
		def start = timeToday(starting).time
		def stop = timeToday(ending).time
		result = start < stop ? currTime >= start && currTime <= stop : currTime <= stop || currTime >= start
	}
	log.trace "timeOk = $result"
	result
}

private hhmm(time, fmt = "h:mm a")
{
	def t = timeToday(time, location.timeZone)
	def f = new java.text.SimpleDateFormat(fmt)
	f.setTimeZone(location.timeZone ?: timeZone(time))
	f.format(t)
}

private timeIntervalLabel()
{
	(starting && ending) ? hhmm(starting) + "-" + hhmm(ending, "h:mm a z") : ""
}
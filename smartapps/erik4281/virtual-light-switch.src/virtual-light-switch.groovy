/**
 *  Virtual Light Switch
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
    name: "Virtual Light Switch",
    namespace: "erik4281",
    author: "Erik Vennink",
    description: "Use this app to create virtual light switches, which can be activated by motion or open/close sensors. These switches can be used to control lights with a separate app. ",
    category: "Safety & Security",
    iconUrl: "http://icons.iconarchive.com/icons/saki/nuoveXT-2/128/Actions-system-shutdown-icon.png",
    iconX2Url: "http://icons.iconarchive.com/icons/saki/nuoveXT-2/128/Actions-system-shutdown-icon.png",
    iconX3Url: "http://icons.iconarchive.com/icons/saki/nuoveXT-2/128/Actions-system-shutdown-icon.png")

/**********
 * Setup  *
 **********/

preferences {
    page(name: "switchPage", title: "Switch on lights when this happens:", install: true, uninstall: true) 
}

def switchPage() {
	dynamicPage(name: "switchPage") {
        section("Control these switches") {
            input "switching", "capability.switch", title: "Which switches?", required:true, multiple:true
        }
        section("Monitor sensors..."){
            input "motionSensor", "capability.motionSensor", title: "Motion Here", required: false, multiple: true
            input "contactSensor", "capability.contactSensor", title: "Contact Opens", required: false, multiple: true
			input "inputSwitch", "capability.switch", title: "Switches (using short-delay time)", required: false, multiple: true
            input "delayMinutes", "number", title: "Off after x minutes", required: false
        }
		section("Monitor illuminance sensor") {
			input "lightSensor", "capability.illuminanceMeasurement", title: "Sensor(s)?", required: false
			input "lightOnValue", "number", title: "On at < (Lux, empty = 100)?", required: false
			input "lightOffValue", "number", title: "Off at > (Lux, empty = 150)?", required: false
		}
		section("Short-delay-mode (for example sleep...)") {
			input "shortModes", "mode", title: "In which mode(s)", required: false, multiple: true
			input "shortStarting", "time", title: "And starting from", required: false
			input "shortEnding", "time", title: "And ending at", required: false
			input "shortDelayMinutes", "number", title: "Off after x minutes", required: false
		}
		section([mobileOnly:true]) {
			input "modes", "mode", title: "Only when mode is", multiple: true, required: false
			label title: "Assign a name", required: false
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
	subscribe(motionSensor, "motion.active", eventHandler)
	subscribe(contactSensor, "contact.open", eventHandler)
	subscribe(inputSwitch, "switch.on", eventHandler)
	subscribe(motionSensor, "motion.inactive", eventOffHandler)
	subscribe(contactSensor, "contact.closed", eventOffHandler)
	subscribe(inputSwitch, "switch.off", eventOffHandler)
	if (lightSensor) {
		subscribe(lightSensor, "illuminance", illuminanceHandler, [filterEvents: false])
	}
}

/******************
 * Event handlers *
 ******************/

def appTouchHandler(evt) {
	log.trace "app started manually"
    activateSwitch()
	def current = motionSensor.currentValue("motion")
	def motionValue = motionSensor.find{it.currentMotion == "active"}
	if (motionValue) {
		state.eventStopTime = null
	}
	else {
		state.eventStopTime = now()
	}
	if ((shortModeOk || shortTimeOk) && shortDelayMinutes) {
		runIn(shortDelayMinutes*60, turnOffAfterDelayShort, [overwrite: false])
	}
	else if (delayMinutes) {
		runIn(delayMinutes*60, turnOffAfterDelay, [overwrite: false])
	}
	else  {
		turnOffAfterDelay()
	}
}

def eventHandler(evt) {
	log.trace "eventHandler: $evt.name: $evt.value"
	state.eventStopTime = null
	if (modeOk && daysOk && timeOk && darkOk && moodOk) {
		log.info "All checks OK, switching on now"
		activateSwitch()
	}
}

def eventOffHandler(evt) {
	log.trace "eventHandler: $evt.name: $evt.value"
	state.eventStopTime = now()
	if (evt.name == "switch" && evt.value == "off" && moodOk) {
		log.info "Switch was set to off. Starting timer to switch off."
        runIn(shortDelayMinutes*60, turnOffAfterDelayShort, [overwrite: false])
	}
	else if (switchOff && modeOk && daysOk && timeOk && moodOk) {
		log.info "Switches are off and all checks passed"
        if ((shortModeOk || shortTimeOk) && shortDelayMinutes) {
			log.info "Now starting short timer to switch off"
            runIn(shortDelayMinutes*60, turnOffAfterDelayShort, [overwrite: false])
		}
		else if (delayMinutes) {
			log.info "Now starting normal timer to switch off"
            runIn(delayMinutes*60, turnOffAfterDelay, [overwrite: false])
		}
		else  {
			log.info "Now starting to switch off"
            turnOffAfterDelay()
		}
	}
	else if (switchOff && moodOk) {
		log.info "Now starting 30 minute timer for backup off switching"
        runIn(30*60, turnOffAfterDelay, [overwrite: false])
	}
}

def illuminanceHandler(evt) {
	if (modeOk && daysOk && timeOk && moodOk) {
		if (state.lastStatus != "off" && evt.integerValue > (lightOffValue ?: 150)) {
			log.info "Light was not off and brightness was too high"
            deactivateSwitch()
		}
		else if (state.eventStopTime) {
			if (state.lastStatus != "off" && switchOff) {
				log.info "Light was not off and not currently activated"
				def elapsed = now() - state.eventStopTime                
				if((shortModeOk || shortTimeOk) && shortDelayMinutes) {
					if (elapsed >= ((shortDelayMinutes ?: 0) * 60000L) - 2000) {
						deactivateSwitch()
					}
				}
				else if(delayMinutes) {
					if (elapsed >= ((delayMinutes ?: 0) * 60000L) - 2000) {
						deactivateSwitch()
					}
				}
			}
			else if (state.lastStatus != "on" && evt.integerValue < (lightOnValue ?: 100) && switchOff != true) {
				log.info "Light was not on and brightness was too low"
                activateSwitch()
			}
		}
		else if (state.lastStatus != "on" && evt.integerValue < (lightOnValue ?: 100)){
			log.info "Light was not on and brightness was too low"
            activateSwitch()
		}
	}
}

/******************
 * Helper methods *
 ******************/

def turnOffAfterDelay() {
	if (state.eventStopTime && state.lastStatus != "off") {
		def elapsed = now() - state.eventStopTime
		if (elapsed >= ((delayMinutes ?: 0) * 60000L) - 2000) {
			log.info "Deactivating started"
			deactivateSwitch()
		}
	}
}

def turnOffAfterDelayShort() {
	if (state.eventStopTime && state.lastStatus != "off") {
		def elapsed = now() - state.eventStopTime
		if (elapsed >= ((shortDelayMinutes ?: 0) * 60000L) - 2000) {
			log.info "Deactivating started"
			deactivateSwitch()
		}
	}
}


def activateSwitch() {
	def current = switching.currentValue('switch')
	def switchValue = switching.find{it.currentSwitch == "off"}
    if (switchValue) {
        startSwitch(switching)
    }
	log.debug "Setting state to On"
    state.lastStatus = "on"
}

def deactivateSwitch() {
	def current = switching.currentValue('switch')
	def switchValue = switching.find{it.currentSwitch == "on"}
    if (switchValue) {
        stopSwitch(switching)
    }
    log.debug "Setting state to Off"
    state.lastStatus = "off"
}

def startSwitch(switchSelect) {
	def check = switchSelect.currentValue('switch')
    log.debug "Check: $check"
    if (check != "[on]") {
		log.trace "Activating Switch '$switchSelect'"
		switchSelect.on()
	}
}

def stopSwitch(switchSelect) {
	def check = switchSelect.currentValue('switch')
    log.debug "Check: $check"
    if (check != "[off]") {
        log.trace "Deactivating Switch '$switchSelect'"
        switchSelect.off()
	}
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

private getAllOkExtra() {
	modeOk && daysOk && timeOk && darkOk
}

private getAllOk() {
	modeOk && daysOk && timeOk
}

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
    log.trace "switchOk = $result"
    result
}

private getDarkOk() {
	def result = true
	if (lightSensor) {
		result = lightSensor.currentIlluminance < (lightOnValue ?: 100)
	}
	else {
		result = true
	}
	log.trace "darkOk = $result"
	result
}

private getModeOk() {
	def result = !modes || modes.contains(location.mode)
	log.trace "modeOk = $result"
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
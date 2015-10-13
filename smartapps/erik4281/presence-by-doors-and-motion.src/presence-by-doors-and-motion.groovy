/**
 *  Presence by doors and motion
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
	name: "Presence by doors and motion",
	namespace: "erik4281",
	author: "Erik Vennink",
	description: "Change presence based on 1 door and 1 or more motion sensors.",
	category: "SmartThings Labs",
	iconUrl: "http://icons.iconarchive.com/icons/iconshock/super-vista-general/128/home-icon.png",
	iconX2Url: "http://icons.iconarchive.com/icons/iconshock/super-vista-general/128/home-icon.png",
	iconX3Url: "http://icons.iconarchive.com/icons/iconshock/super-vista-general/128/home-icon.png")

/**********
 * Setup  *
 **********/

preferences {
	section("Monitor this door..."){
		input "contactSensor", "capability.contactSensor", title: "Contact opens", required: true, multiple: false
	}
	section("... and this motion sensor(s)...") {
		input "motionSensor", "capability.motionSensor", title: "Motion here", required: true, multiple: true
	}
	section("...override presence with these sensor(s)") {
		input "overrideSensor", "capability.motionSensor", title: "Motion here", required: false, multiple: true
	}
	section("Switch to this mode for home...") {
		input "homeMode", "mode", title: "Change mode to?", required: true
		input "homeAlarm", "enum", title: "Set SHM mode to?" , required: false, multiple:false, options: ["off","stay","away"]
		input "homeOn", "capability.switch", title: "Turn on switches?", required: false, multiple: true
		input "homeOff", "capability.switch", title: "Turn off switches?", required: false, multiple: true
	}
	section("Switch to this mode for away...") {
		input "awayMode", "mode", title: "Change mode to?", required: true
		input "awayAlarm", "enum", title: "Set SHM mode to?" , required: false, multiple:false, options: ["off","stay","away"]
		input "awayOn", "capability.switch", title: "Turn on switches?", required: false, multiple: true
		input "awayOff", "capability.switch", title: "Turn off switches?", required: false, multiple: true
	}
	section("Use this delay for away mode...") {
		input "delayMinutes", "number", title: "Change after X minutes", required: true
	}
	section("Also monitor for sleepmode (to enable away from sleep") {
		input "sleepMode", "mode", title: "Monitor sleep-mode", required: false, multiple: true
	}
	section("Send PUSH...") {
		input "pushOn", "enum", title: "Send a push notification?", options: ["Yes", "No"], required: true
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
	initialize()
}

def initialize() {
	subscribe(motionSensor, "motion.active", motionActiveHandler)
	subscribe(motionSensor, "motion.inactive", motionInactiveHandler)
	subscribe(contactSensor, "contact.open", contactOpenHandler)
	subscribe(contactSensor, "contact.closed", contactCloseHandler)
}

/******************
 * Event handlers *
 ******************/

def motionActiveHandler(evt) {
	log.debug "motionActiveHandler"
	state.motionState = "active"
    state.motionStop = null
	log.info state.motionState
    log.info state.motionStop
	if (overrideSensor) {
		def current = overrideSensor.currentValue('motion')
		def overrideValue = overrideSensor.find{it.currentMotion == "active"}
		if (overrideValue) {
			changeHome()
		}
	}
}

def motionInactiveHandler(evt) {
	log.debug "motionInactiveHandler"
	if (motionOk) {
		state.motionState = "active"
        state.motionStop = null
	}
	else { 
		state.motionState = "inactive"
        state.motionStop = now()
	}
	log.info state.motionState
    log.info state.motionStop
}

def contactOpenHandler(evt) {
	log.debug "contactOpenHandler"
	state.contactState = "open"
	log.info state.contactState
	changeHome()
}

def contactCloseHandler(evt) {
	log.debug "contactCloseHandler"
	state.contactState = "closed"
	log.info state.contactState
	log.info "Changing to away in ${delayMinutes} minutes"
	runIn((delayMinutes*60), changeAway, [overwrite: true])
}

/********************
 * Actuator methods *
 ********************/

def changeHome() {
	log.debug "Change home mode"
	if (awayModeOk) {
		log.debug "Changing to home"
		state.awayState = "home"
		changeMode(homeMode)
		if (homeAlarm) {
			sendLocationEvent(name: "alarmSystemStatus", value: homeAlarm)
		}
		if (pushOn == "Yes") {
			sendPush("Arrive: Alarm switched off.")
		}
		else {
			sendNotificationEvent("Arrive: Alarm switched off.")
		}
		if (homeOn) {	
			homeOn.each {light ->
				light.on()
				light.on()
			}
		}
		if (homeOff) {
			homeOff.each {light ->
				light.off()
				light.off()
			}
		}
	}
}

def changeAway() {
	log.debug "Change away mode"
	if (state.contactState == "closed" && state.motionState == "inactive" && (homeModeOk || sleepModeOk)) {
		def elapsed = now() - state.motionStop
        log.info elapsed
		if (elapsed >= ((delayMinutes ?: 0) * 60000L) - 60000) {
		
        log.debug "Changing to away"
		state.awayState = "away"
		changeMode(awayMode)
		if (awayAlarm) {
			sendLocationEvent(name: "alarmSystemStatus", value: awayAlarm)
		}
		if (pushOn == "Yes") {
			sendPush("Leave: Alarm switched on.")
		}
		else {
			sendNotificationEvent("Leave: Alarm switched on.")
		}
		if (awayOn) {	
			awayOn.each {light ->
				light.on()
				light.on()
			}
		}
		if (awayOff) {
			awayOff.each {light ->
				light.off()
				light.off()
			}
		}
        }
	}
}

/******************
 * Helper methods *
 ******************/

def changeMode(newMode) {
	if (newMode && location.mode != newMode) {
		if (location.modes?.find{it.name == newMode}) {
			setLocationMode(newMode)
		}
		else {
		}
	}
}

/***********
 * Checks  *
 ***********/

private getAwayModeOk() {
	def result = awayMode.contains(location.mode)
	log.trace "AwayModeOk = $result"
	result
}

private getHomeModeOk() {
	def result = homeMode.contains(location.mode)
	log.trace "HomeModeOk = $result"
	result
}

private getSleepModeOk() {
	def result = sleepMode.containt(location.mode)
	log.trace "SleepModeOk = $result"
	result
}

private getMotionOk() {
	def result = true
	if (motionSensor) {
		def current = motionSensor.currentValue('motion')
		def motionValue = motionSensor.find{it.currentMotion == "active"}
		if (motionValue) {
			result = true
		}
		else {
			result = false
		}
	}
	else {
		result = true
	}
	log.trace "motionOk = $result"
	result
}

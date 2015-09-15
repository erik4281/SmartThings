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
    page(name: "switchPage", title: "Switch on lights when this happens:", nextPage: "optionsPage", uninstall: true) 
    page(name: "optionsPage", title: "Use these options:", install: true, uninstall: true) 
}

def switchPage() {
	dynamicPage(name: "switchPage") {
        section("Monitor sensors..."){
            input "motionSensor", "capability.motionSensor", title: "Motion Here", required: false, multiple: true
            input "contactSensor", "capability.contactSensor", title: "Contact Opens", required: false, multiple: true
			input "inputSwitch", "capability.switch", title: "Switches (using short-delay time)", required: false, multiple: true
            input "delayMinutes", "number", title: "Off after x minutes", required: false
        }
        section("Switch ON..."){
            input "triggerModes", "mode", title: "System Changes Mode", required: false, multiple: true
            input "timeOfDay", "time", title: "At a Scheduled Time", required: false
        }
        section("Switch OFF..."){
            input "triggerModesOff", "mode", title: "System Changes Mode", required: false, multiple: true
            input "timeOfDayOff", "time", title: "At a Scheduled Time", required: false
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
	}
}

def optionsPage() {
	dynamicPage(name: "optionsPage") {
        section("Control these switches") {
            input "switching", "capability.switch", title: "Which switches?", required:true, multiple:true
        }
		section("Timing options") {
			input "starting", "time", title: "Starting from", required: false
			input "ending", "time", title: "Ending at", required: false
			input "days", "enum", title: "Only on certain days of the week", multiple: true, required: false,
				options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
			input "modes", "mode", title: "Only when mode is", multiple: true, required: false
		}
		section([mobileOnly:true]) {
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
	subscribe(motionSensor, "motion", motionHandler)
	subscribe(contactSensor, "contact", contactHandler)
	subscribe(inputSwitch, "switch", switchHandler)
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
	log.trace "app started manually"
    activateSwitch()
	def current = motionSensor.currentValue("motion")
	def motionValue = motionSensor.find{it.currentMotion == "active"}
	if (motionValue) {
    	log.debug "motionValue = true"
		state.motionStopTime = null
	}
	else {
    	state.motionStopTime = now()
    }
    if((shortModeOk || shortTimeOk) && shortDelayMinutes) {
        log.debug "Short delay started"
        runIn(shortDelayMinutes*60, turnOffMotionAfterDelayShort, [overwrite: false])
        log.info "Delay short: $shortDelayMinutes minutes"
    }
    else if(delayMinutes) {
        log.debug "Normal delay started"
        runIn(delayMinutes*60, turnOffMotionAfterDelay, [overwrite: false])
        log.info "Delay: $delayMinutes minutes"
	} 
	else {
		log.debug "Backup scenario started"
        turnOffMotionAfterDelay()
	}
}

def switchHandler(evt) {
	log.trace "switchHandler()"
	def current = inputSwitch.currentValue('switch')
	def switchValue = inputSwitch.find{it.currentSwitch == "on"}
	if (switchValue && darkOk && allOk) {
    	log.debug "motionValue = true"
		activateSwitch()
        state.motionStopTime = null
	}
	else if (switchValue) {
    	log.debug "motionValue = true"
		state.motionStopTime = null
	}
	else {
    	state.motionStopTime = now()
    }
    if(shortDelayMinutes) {
        log.debug "Short delay started"
        runIn(shortDelayMinutes*60, turnOffMotionAfterDelayShort, [overwrite: false])
        log.info "Delay short: $shortDelayMinutes minutes"
    }
	else {
		log.debug "Backup scenario started"
        turnOffMotionAfterDelay()
	}
}

def motionHandler(evt) {
	log.trace "motionHandler"
	def current = motionSensor.currentValue("motion")
	def motionValue = motionSensor.find{it.currentMotion == "active"}
	if (switchOk && allOk) {
    	log.debug "allOk"
        if (motionValue) {
            log.debug "motionValue = true"
            state.motionStopTime = null
            if (darkOk && allOk) {
	            log.debug "allOk"
                activateSwitch()
            }
        }
        else {
            state.motionStopTime = now()
		    if((shortModeOk || shortTimeOk) && shortDelayMinutes) {
		        log.debug "Short delay started"
                runIn(shortDelayMinutes*60, turnOffMotionAfterDelayShort, [overwrite: false])
		        log.info "Delay short (motion): $shortDelayMinutes minutes"
		    }
            else if(delayMinutes) {
                log.debug "Normal delay started"
                runIn(delayMinutes*60, turnOffMotionAfterDelay, [overwrite: false])
                log.info "Delay (motion): $delayMinutes minutes"
            } 
            else {
                log.debug "Backup scenario started"
                turnOffMotionAfterDelay()
            }
        }
    }
    else if (switchOk) {
        if (motionValue) {
            state.motionStopTime = null
            log.debug "Timer stopped"
        }
        else {
            state.motionStopTime = now()
            log.debug "Backup scenario started"
            runIn(30*60, turnOffMotionAfterDelay, [overwrite: false])
            log.info "Delay (motion): 30 minutes (backup off switch)"
        }
    }
    else {
        if (motionValue) {
            state.motionStopTime = null
            log.debug "Timer stopped"
        }
        else {
            state.motionStopTime = now()
            log.debug "Backup scenario started"
        }
    }
}

def contactHandler(evt) {
	log.trace "contactHandler"
	def current = contactSensor.currentValue("contact")
	def contactValue = contactSensor.find{it.currentContact == "open"}
	if (switchOk && allOk) {
        log.debug "allOk"
        if (contactValue) {
            state.motionStopTime = null
            if (darkOk && allOk) {
	            log.debug "allOk"
                activateSwitch()
            }
        }
        else {
            state.motionStopTime = now()
		    if((shortModeOk || shortTimeOk) && shortDelayMinutes) {
		        log.debug "Short delay started"
                runIn(shortDelayMinutes*60, turnOffMotionAfterDelayShort, [overwrite: false])
		        log.info "Delay short (contact): $shortDelayMinutes minutes"
		    }
            else if(delayMinutes) {
                log.debug "Normal delay started"
                runIn(delayMinutes*60, turnOffMotionAfterDelay, [overwrite: false])
                log.info "Delay (contact): $delayMinutes minutes"
            } 
            else {
                log.debug "Backup scenario started"
                turnOffMotionAfterDelay()
            }
        }
    }
    else if (switchOk) {
        if (motionValue) {
            state.motionStopTime = null
            log.debug "Timer stopped"
        }
        else {
            state.motionStopTime = now()
            log.debug "Backup scenario started"
            runIn(30*60, turnOffMotionAfterDelay, [overwrite: false])
            log.info "Delay (motion): 30 minutes (backup off switch)"
        }
    }
    else {
        if (motionValue) {
            state.motionStopTime = null
            log.debug "Timer stopped"
        }
        else {
            state.motionStopTime = now()
            log.debug "Backup scenario started"
        }
    }
}

def illuminanceHandler(evt) {
	log.trace "illuminanceHandler()"
    if (allOk) {
    	log.debug "allOk"
        log.info "state.lastStatus: $state.lastStatus"
        log.info "evt.integerValue: $evt.integerValue"
        log.info "state.motionStopTime: $state.motionStopTime"
        if (state.lastStatus != "off" && evt.integerValue > (lightOffValue ?: 150)) {
            log.debug "State is On and brightness is higher than trigger value"
            deactivateSwitch()
        }
        else if (state.motionStopTime) {
            log.debug "Timer is running (so no motion at the moment)"
            if (state.lastStatus != "off") {
                log.debug "State is on"
                def elapsed = now() - state.motionStopTime                
			    if((shortModeOk || shortTimeOk) && shortDelayMinutes) {
 			       log.debug "Switch off within short delay time"
                   if (elapsed >= ((shortDelayMinutes ?: 0) * 60000L) - 2000) {
			        	deactivateSwitch()
                    }
			    }
				else if(delayMinutes) {
                	log.debug "Switch off within normal delay time"
                    if (elapsed >= ((delayMinutes ?: 0) * 60000L) - 2000) {
                    	deactivateSwitch()
                    }
                }
            }
            else if (state.lastStatus != "on" && evt.integerValue < (lightOnValue ?: 100)) {
            	log.debug "State is off and brightness is lower than trigger value. Timer was already running."
                if (switchOk) {
                }
                else {
                    activateSwitch()
                }
            }
        }
        else if (state.lastStatus != "on" && evt.integerValue < (lightOnValue ?: 100)){
            log.debug "State is Off and brightness is lower than trigger value"
            activateSwitch()
        }
	}
}

def modeChangeHandler(evt) {
	if (evt.value in triggerModes) {
		log.debug "Activating switch"
        activateSwitch()
	}
	if (evt.value in triggerModesOff) {
		log.debug "Deactivating switch"
        deactivateSwitch()
	}
}

def scheduledTimeHandler() {
	log.trace "scheduledTimeHandler()"
	log.debug "Activating switch"
    activateSwitch()
}

def scheduledTimeOffHandler() {
	log.trace "scheduledTimeOffHandler()"
	log.debug "Deactivating switch"
    deactivateSwitch()
}

/******************
 * Helper methods *
 ******************/

def turnOffMotionAfterDelay() {
	log.trace "In turnOffMotionAfterDelay, state.motionStopTime = $state.motionStopTime, state.lastStatus = $state.lastStatus"
	log.debug "Turning off after (normal) delay"
    if (state.motionStopTime && state.lastStatus != "off") {
		def elapsed = now() - state.motionStopTime
        if (elapsed >= ((delayMinutes ?: 0) * 60000L) - 2000) {
        	deactivateSwitch()
		}
	}
}

def turnOffMotionAfterDelayShort() {
	log.trace "In turnOffMotionAfterDelayShort, state.motionStopTime = $state.motionStopTime, state.lastStatus = $state.lastStatus"
	log.debug "Turning off after (short) delay"
    if (state.motionStopTime && state.lastStatus != "off") {
		def elapsed = now() - state.motionStopTime
        if (elapsed >= ((shortDelayMinutes ?: 0) * 60000L) - 2000) {
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
    	//log.debug "inputswitch present"
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
    	//log.debug "no inputswitch"
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
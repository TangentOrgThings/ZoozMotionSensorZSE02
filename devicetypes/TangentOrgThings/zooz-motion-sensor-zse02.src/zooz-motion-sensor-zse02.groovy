// vim :set ts=2 sw=2 sts=2 expandtab smarttab :
/**
 *  Zooz ZSE02 Motion Sensor
 *
 *  Copyright 2016 Brian Aker
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

def getDriverVersion() {
  return "v2.21"
}

def getAssociationGroup() {
  return 1
}

metadata {
  definition (name: "Zooz Motion Sensor ZSE02", namespace: "TangentOrgThings", author: "Brian Aker") {
    capability "Battery"
    capability "Configuration"
    capability "Motion Sensor"
    capability "Refresh"
    capability "Sensor"
    
    attribute "buttonPushed", "enum", ["clear", "detected"]
    attribute "configured", "enum", ["false", "true"]
    attribute "reset", "enum", ["false", "true"]
    attribute "driverVersion", "string"
    attribute "MSR", "string"
    attribute "Manufacturer", "string"
    attribute "ManufacturerCode", "string"
    attribute "ProduceTypeCode", "string"
    attribute "ProductCode", "string"
    attribute "WakeUp", "string"
    attribute "WirelessConfig", "string"
    attribute "firmwareVersion", "string"
  }

  // zw:S type:0701 mfr:0152 prod:0500 model:0003 ver:0.01 zwv:3.95 lib:06 cc:5E,85,59,71,80,5A,73,84,72,86 role:06 ff:8C07 ui:8C07
  fingerprint type: "0701", mfr: "0152", prod: "0500", model: "0003", ui: "3079", cc: "59, 85, 80, 5A, 72, 71, 73, 86, 84, 5E",  deviceJoinName: "Zooz Motion Sensor ZSE02"
  fingerprint type: "0701", mfr: "027A", prod: "0500", model: "0003", ui: "3079", deviceJoinName: "Zooz Motion Sensor ZSE02"

  simulator 
  {
    // TODO: define status and reply messages here
  }

  tiles (scale: 2)
  {
    multiAttributeTile(name:"main", type: "generic", width: 6, height: 4)
    {
      tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
        attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
        attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
      }
    }
    valueTile("buttonPushed", "device.buttonPushed", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
      state "clear", backgroundColor:"#00FF00"
      state "detected", backgroundColor:"#e51426"
    }
    valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 2, height: 2)
    {
      state "battery", label:'${currentValue}', unit:"%"
    }
    valueTile("driverVersion", "device.driverVersion", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
      state "driverVersion", label:'${currentValue}'
    }
    standardTile("configure", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2)
    {
      state "default", label:"", action:"configuration.configure", icon:"st.secondary.configure"
    }
    standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2)
    {
      state "default", label:'', action: "refresh.refresh", icon: "st.secondary.refresh"
    }
    standardTile("reset", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2)
    {
      state "false", label:'', backgroundColor:"#ffffff"
      state "true", label:'reset', backgroundColor:"#e51426"
    }
    main(["main"])
    details(["main", "buttonPushed", "battery", "driverVersion", "configure", "refresh", "reset"])
  }
}

def installed() {
  sendEvent([name: "driverVersion", value: getDriverVersion(), isStateChange: true])
  sendEvent([name: "configured", value: "false", isStateChange: true])
  state.configured = false
}

def updated() {
  sendEvent(name: "driverVersion", value: getDriverVersion(), displayed:true)
}

def parse(String description) {
  def result = null

  if (description.startsWith("Err")) {
    if (description.startsWith("Err 106")) {
      if (state.sec) {
        log.debug description
      } else {
        result = createEvent(
          descriptionText: "This device failed to complete the network security key exchange. If you are unable to control it via SmartThings, you must remove it from your network and add it again.",
          eventType: "ALERT",
          name: "secureInclusion",
          value: "failed",
          isStateChange: true,
        )
      }
    } else {
      result = createEvent(value: description, descriptionText: description)
    }
  } else if (description != "updated") {
    def cmd = zwave.parse(description, [0x20: 1, 0x85: 2, 0x59: 1, 0x71: 3, 0x80: 1, 0x5A: 1, 0x84: 2, 0x72: 2, 0x86: 1])
	
    if (cmd) {
      result = zwaveEvent(cmd)
      
      if (!result) {
        log.warning "Parse Failed and returned ${result} for command ${cmd}"
        result = createEvent(value: description, descriptionText: description)
      }
    } else {
      log.info "Non-parsed event: ${description}"
      result = createEvent(value: description, descriptionText: description)
    }
  }
    
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd)
{
  def result = [createEvent(descriptionText: "${device.displayName} woke up", displayed: true)]
  def cmds = []
  
  if (state.configured) {
    if (zwaveHubNodeId == 1) { // Only check on battery if this controller is the main controller
      if (!state.lastbat || (new Date().time) - state.lastbat > 53*60*60*1000) {
        result << response(zwave.batteryV1.batteryGet())
      }
    }
  }
 
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd)
{
  log.info "Executing zwaveEvent 5A (DeviceResetLocallyV1) : 01 (DeviceResetLocallyNotification) with cmd: $cmd" 
  createEvent(name: "reset", value: "reset", descriptionText: cmd.toString(), isStateChange: true, displayed: true) 
} 

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv1.ManufacturerSpecificReport cmd) 
{
  def result = []
  
  def manufacturerCode = String.format("%04X", cmd.manufacturerId)
  def productTypeCode = String.format("%04X", cmd.productTypeId)
  def productCode = String.format("%04X", cmd.productId)
  def wirelessConfig = "ZWP"
  
  result << createEvent(name: "ManufacturerCode", value: manufacturerCode)
  result << createEvent(name: "ProduceTypeCode", value: productTypeCode)
  result << createEvent(name: "ProductCode", value: productCode)
  result << createEvent(name: "WirelessConfig", value: wirelessConfig)

  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  updateDataValue("MSR", msr)	updateDataValue("MSR", msr)
  updateDataValue("manufacturer", cmd.manufacturerName)
  if (!state.manufacturer) {
    state.manufacturer= cmd.manufacturerName
  }
  
  result << createEvent([name: "MSR", value: "$msr", descriptionText: "$device.displayName", isStateChange: false])
  result << createEvent([name: "Manufacturer", value: "${cmd.manufacturerName}", descriptionText: "$device.displayName", isStateChange: false])
  
  return result
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
  log.info "Executing zwaveEvent 86 (VersionV1) with cmd: $cmd"
  def text = "$device.displayName: firmware version: ${cmd.applicationVersion}.${cmd.applicationSubVersion}, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
  createEvent([name: "firmwareVersion", value: "${cmd.applicationVersion}.${cmd.applicationSubVersion}", descriptionText: "$text", isStateChange: false])
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd)
{
  def results = []
 
  def map = [ name: "battery", unit: "%" ]
  if (cmd.batteryLevel == 0xFF) {
    map.value = 1
    map.descriptionText = "${device.displayName} has a low battery"
    map.isStateChange = true
  } else {
    map.value = cmd.batteryLevel
  }
  state.lastbat = new Date().time
  
  results << createEvent(map)
  
  return results
}

def motionEvent(value) {
  def map = [name: "motion"]
  if (value != 0) {
    map.value = "active"
    map.descriptionText = "$device.displayName detected motion"
  } else {
    map.value = "inactive"
    map.descriptionText = "$device.displayName motion has stopped"
  }
  createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
  motionEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.alarmv2.AlarmReport cmd) {
  log.debug "ALARM: ${description}"
  motionEvent(cmd.value)
}

//  payload: 00 00 00 FF 07 00 01 03
def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
  def result = []
  if (cmd.notificationType == 0x07) {
    if (cmd.event == 0x00) {
      if (cmd.eventParameter == [8]) {
        result << motionEvent(0)
      } else if (cmd.eventParameter == [3]) { // payload : 00 00 00 FF 07 00 01 03
        result << createEvent(descriptionText: "$device.displayName covering replaced", isStateChange: true, displayed: false)
        result << createEvent(name: "buttonPushed", value: "detected", descriptionText: "$device.displayName covering was removed", isStateChange: true)
      } else {
        result << motionEvent(0)
      }
    } else if (cmd.event == 0x03) {
      result << createEvent(name: "acceleration", value: "active", descriptionText: "$device.displayName has been deactivated by the switch.")
    } else if (cmd.event == 0x08) {
      result << motionEvent(255)
    }
  } else {
    result << createEvent(descriptionText: cmd.toString(), isStateChange: true)
  }

  return result
}

def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd)
{
  def result = []
  if (cmd.groupingIdentifier == 1)
  {
    if (cmd.nodeId.any { it == zwaveHubNodeId }) 
    {
      result << createEvent(descriptionText: "$device.displayName is associated in group ${cmd.groupingIdentifier}")
      result << createEvent(name: "configured", value: "true", descriptionText: "$device.displayName not associated with hub", isStateChange: true)
      state.configured = false
    } else {
      result << createEvent(name: "configured", value: "false", descriptionText: "$device.displayName not associated with hub", isStateChange: true)
      state.configured = true
    }
  }
  
  return result
}

def zwaveEvent(physicalgraph.zwave.Command cmd)
{
  [createEvent(descriptionText: cmd.toString(), isStateChange: false)]
}


def refresh()
{
  log.debug "refresh() is called"
  state.refresh = false
  createEvent(descriptionText: "refresh will be called during next wakeup", displayed: true) 
}

def configure() {
  state.configured = false
}

private command(physicalgraph.zwave.Command cmd) {
  if (state.sec) {
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  } else {
    cmd.format()
  }
}

private commands(commands, delay=200) {
  delayBetween(commands.collect{ command(it) }, delay)
}

package it.unibo.pulvreakt.dsl.system.model

import arrow.core.NonEmptySet

/**
 * Represents which [logicalDevices] are involved in the system.
 */
data class SystemSpecification(val logicalDevices: NonEmptySet<LogicalDeviceSpecification>)

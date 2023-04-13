package it.nicolasfarabegoli.pulverization.runtime.spawner

import it.nicolasfarabegoli.pulverization.core.Behaviour
import it.nicolasfarabegoli.pulverization.runtime.componentsref.ActuatorsRef
import it.nicolasfarabegoli.pulverization.runtime.componentsref.CommunicationRef
import it.nicolasfarabegoli.pulverization.runtime.componentsref.SensorsRef
import it.nicolasfarabegoli.pulverization.runtime.componentsref.StateRef
import it.nicolasfarabegoli.pulverization.runtime.utils.BehaviourLogicType
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal class BehaviourSpawnable<S : Any, C : Any, SS : Any, AS : Any, O : Any>(
    private val behaviour: Behaviour<S, C, SS, AS, O>,
    private val behaviourLogic: BehaviourLogicType<S, C, SS, AS, O>,
    private val behaviourStateRef: StateRef<S>,
    private val behaviourCommRef: CommunicationRef<C>,
    private val behaviourSensorsRef: SensorsRef<SS>,
    private val behaviourActuatorsRef: ActuatorsRef<AS>,
) : Spawnable {
    private var jobRef: Job? = null
    override suspend fun spawn(): Job = coroutineScope {
        jobRef = launch {
            behaviour.initialize()
            behaviourLogic(behaviour, behaviourStateRef, behaviourCommRef, behaviourSensorsRef, behaviourActuatorsRef)
            behaviour.finalize()
        }
        return@coroutineScope jobRef!!
    }

    override suspend fun kill() {
        jobRef?.let {
            it.cancelAndJoin()
            behaviour.finalize()
        }
    }
}

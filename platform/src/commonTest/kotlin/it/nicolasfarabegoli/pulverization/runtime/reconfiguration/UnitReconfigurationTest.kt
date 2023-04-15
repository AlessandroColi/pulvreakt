package it.nicolasfarabegoli.pulverization.runtime.reconfiguration

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import it.nicolasfarabegoli.pulverization.dsl.v2.model.Behaviour
import it.nicolasfarabegoli.pulverization.dsl.v2.model.ComponentType
import it.nicolasfarabegoli.pulverization.dsl.v2.model.Sensors
import it.nicolasfarabegoli.pulverization.runtime.communication.CommManager
import it.nicolasfarabegoli.pulverization.runtime.communication.Communicator
import it.nicolasfarabegoli.pulverization.runtime.communication.RemotePlaceProvider
import it.nicolasfarabegoli.pulverization.runtime.componentsref.ComponentRef
import it.nicolasfarabegoli.pulverization.runtime.context.ExecutionContext
import it.nicolasfarabegoli.pulverization.runtime.dsl.v2.RPP
import it.nicolasfarabegoli.pulverization.runtime.dsl.v2.TestCommunicator
import it.nicolasfarabegoli.pulverization.runtime.dsl.v2.TestReconfigurator
import it.nicolasfarabegoli.pulverization.runtime.dsl.v2.model.DeploymentUnitRuntimeConfiguration
import it.nicolasfarabegoli.pulverization.runtime.dsl.v2.model.Host
import it.nicolasfarabegoli.pulverization.runtime.dsl.v2.model.reconfigurationRules
import it.nicolasfarabegoli.pulverization.runtime.dsl.v2.pulverizationRuntime
import it.nicolasfarabegoli.pulverization.runtime.spawner.SpawnerManager
import it.nicolasfarabegoli.pulverization.runtime.utils.BehaviourTest
import it.nicolasfarabegoli.pulverization.runtime.utils.HighCpuUsage
import it.nicolasfarabegoli.pulverization.runtime.utils.Host1
import it.nicolasfarabegoli.pulverization.runtime.utils.Host2
import it.nicolasfarabegoli.pulverization.runtime.utils.SensorsContainerTest
import it.nicolasfarabegoli.pulverization.runtime.utils.availableHosts
import it.nicolasfarabegoli.pulverization.runtime.utils.behaviourTestLogic
import it.nicolasfarabegoli.pulverization.runtime.utils.createComponentsRefs
import it.nicolasfarabegoli.pulverization.runtime.utils.highCpuUsageFlow
import it.nicolasfarabegoli.pulverization.runtime.utils.sensorsLogicTest
import it.nicolasfarabegoli.pulverization.runtime.utils.setupOperationMode
import it.nicolasfarabegoli.pulverization.runtime.utils.setupRefs
import it.nicolasfarabegoli.pulverization.runtime.utils.systemConfig
import it.nicolasfarabegoli.pulverization.utils.PulverizationKoinModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.serializer
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.KoinTest

class UnitReconfigurationTest : FreeSpec(), KoinTest {
    private val module = module {
        single { CommManager() }
        single<Communicator> { TestCommunicator() }
        single<RemotePlaceProvider> { RPP }
        single<ExecutionContext> {
            object : ExecutionContext {
                override val host: Host = Host2
                override val deviceID: String = "1"
            }
        }
    }
    private val flow = MutableSharedFlow<Pair<ComponentType, Host>>(1)

    init {
        "The ReconfigurationUnit" - {
            PulverizationKoinModule.koinApp = koinApplication { modules(module) }
            val config: DeploymentUnitRuntimeConfiguration<Unit, Unit, Int, Unit, Unit> =
                pulverizationRuntime(systemConfig, "smartphone", availableHosts) {
                    BehaviourTest() withLogic ::behaviourTestLogic startsOn Host2
                    SensorsContainerTest() withLogic ::sensorsLogicTest startsOn Host2

                    withReconfigurator { TestReconfigurator(flow, MutableSharedFlow()) }
                    withCommunicator { TestCommunicator() }
                    withRemotePlaceProvider { RPP }

                    reconfigurationRules {
                        onDevice {
                            HighCpuUsage reconfigures { Behaviour movesTo Host1 }
                        }
                    }
                }
            val componentsRef: ComponentsRefsContainer<Unit, Unit, Int, Unit> =
                config.createComponentsRefs(serializer(), serializer(), serializer(), serializer())
            val spawner = SpawnerManager(
                config.runtimeConfiguration.componentsRuntimeConfiguration,
                componentsRef,
            )
            val unitReconfigurator = UnitReconfigurator(
                config.runtimeConfiguration.reconfiguratorProvider(),
                config.reconfigurationRules(),
                componentsRef,
                spawner,
                config.startupComponent(Host2),
            )
            "when the condition of an event" - {
                "should change mode accordingly" {
                    componentsRef.setupRefs()
                    componentsRef.setupOperationMode(config.hostComponentsStartupMap(), Host2)
                    unitReconfigurator.initialize()
                    spawner.spawn(Behaviour)
                    spawner.spawn(Sensors)
                    spawner.activeComponents() shouldBe setOf(Behaviour, Sensors)
                    componentsRef.behaviourRefs.sensorsRef.operationMode shouldBe ComponentRef.OperationMode.Local
                    componentsRef.sensorsToBehaviourRef.operationMode shouldBe ComponentRef.OperationMode.Local
                    // Trigger a reconfiguration
                    highCpuUsageFlow.emit(0.95)
                    delay(100) // wait the reconfiguration
                    componentsRef.sensorsToBehaviourRef.operationMode shouldBe ComponentRef.OperationMode.Remote
                    spawner.activeComponents() shouldBe setOf(Sensors)
                    unitReconfigurator.finalize()
                }
            }
        }
    }
}

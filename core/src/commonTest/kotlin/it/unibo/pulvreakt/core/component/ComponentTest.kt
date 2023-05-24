package it.unibo.pulvreakt.core.component

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import it.unibo.pulvreakt.core.communicator.Communicator
import it.unibo.pulvreakt.core.communicator.Mode
import it.unibo.pulvreakt.core.unit.NewConfiguration
import it.unibo.pulvreakt.core.unit.UnitManager
import it.unibo.pulvreakt.core.utils.PulvreaktKoinContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class MyComponent : AbstractComponent<Int>() {
    override val name: String = this::class.simpleName!!
    override suspend fun execute(): Either<String, Unit> = either {
        send<Int, MyComponent>(10).bind()
        send<Int, MyComponent>(20).bind()
    }
}

class FakeCommunicator : Communicator {
    override suspend fun communicatorSetup(source: Component<*>, destination: Component<*>) = Unit
    override suspend fun sendToComponent(message: ByteArray): Either<String, Unit> = Unit.right()
    override suspend fun receiveFromComponent(): Either<String, Flow<ByteArray>> = emptyFlow<ByteArray>().right()
    override suspend fun initialize(): Either<String, Unit> = Unit.right()
    override suspend fun finalize(): Either<String, Unit> = Unit.right()
    override fun setMode(mode: Mode) = Unit
}

class FakeUnitManager : UnitManager {
    override fun configurationUpdates(): Flow<NewConfiguration> = emptyFlow()
    override suspend fun initialize(): Either<String, Unit> = Unit.right()
    override suspend fun finalize(): Either<String, Unit> = Unit.right()
}

class ComponentTest : FreeSpec() {
    private val koinModule = module {
        factory<Communicator> { FakeCommunicator() }
        single<UnitManager> { FakeUnitManager() }
    }

    init {
        PulvreaktKoinContext.koinApp = koinApplication { modules(koinModule) }
        "A Component" - {
            "should send messages to other linked components" {
                val myComponent = MyComponent().apply {
                    setupComponentLink(this)
                    initialize() shouldBe Either.Right(Unit)
                }
                myComponent.execute() shouldBe Either.Right(Unit)
                myComponent.finalize()
            }
        }
    }
}

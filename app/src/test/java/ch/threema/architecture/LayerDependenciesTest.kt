package ch.threema.architecture

import ch.threema.app.BuildConfig
import ch.threema.app.ThreemaApplication
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.utils.executor.HandlerExecutor
import ch.threema.architecture.ArchitectureTestUtils.DoNotIncludeAndroidTests
import ch.threema.logging.LogBackendFactoryImpl
import ch.threema.logging.backend.DebugLogFileBackend
import ch.threema.storage.factories.PollModelFactory
import ch.threema.storage.models.ConversationModel
import com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.junit.ArchUnitRunner
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.library.Architectures
import kotlin.reflect.KClass
import org.junit.runner.RunWith

@Suppress("unused")
@RunWith(ArchUnitRunner::class)
@AnalyzeClasses(packages = [ArchitectureDefinitions.THREEMA_ROOT_PACKAGE], importOptions = [DoNotIncludeAndroidTests::class])
class LayerDependenciesTest {

    @ArchTest
    val appLayerAccess: ArchRule = ArchitectureDefinitions.getLayeredArchitecture()
        .whereLayer(ArchitectureDefinitions.APP)
        .mayNotBeAccessedByAnyLayer()
        .ignoreDependency(
            nameMatching(".*"),
            nameMatching("ch\\.threema\\.app\\.BuildConfig"),
        )
        // Storage layer may access services and utils
        .ignoreDependency(
            nameMatching("ch\\.threema\\.storage\\..*"),
            nameMatching("ch\\.threema\\.app\\.services\\..*"),
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.storage\\..*"),
            nameMatching("ch\\.threema\\.app\\.preference\\.service\\..*"),
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.storage\\..*"),
            nameMatching("ch\\.threema\\.app\\.utils\\..*"),
        )
        // Data layer may access event buses, utils, multi-device, and reflection tasks
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.managers\\..*"),
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.eventbus\\..*"),
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.utils\\..*"),
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.multidevice\\..*"),
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.tasks\\..*"),
        )
        // TODO(ANDR-4361): Remove this
        // Data layer needs to access old services to keep caches in sync
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.services\\..*"),
        )
        // TODO(ANDR-3325): Remove
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\.repositories\\.EmojiReactionsRepository"),
            nameMatching("ch\\.threema\\.app\\.emojis\\.EmojiUtil"),
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.localcrypto\\.LocalCryptoFeatureModule.*"),
            nameMatching("ch\\.threema\\.app\\..*"),
        )
        .ignoreDependency(ConversationModel::class, MessageReceiver::class)
        .ignoreDependency(ConversationModel::class, GroupMessageReceiver::class)
        .ignoreDependency(ConversationModel::class, DistributionListMessageReceiver::class)
        .ignoreDependency(ConversationModel::class, ContactMessageReceiver::class)
        .ignoreDependency(PollModelFactory::class, ContactMessageReceiver::class)
        .ignoreDependency(PollModelFactory::class, GroupMessageReceiver::class)
        .ignoreDependency(PollModelFactory::class, MessageReceiver::class)
        .ignoreDependency(LogBackendFactoryImpl::class, BuildConfig::class)
        .ignoreDependency(LogBackendFactoryImpl::class, ThreemaApplication::class)
        .ignoreDependency(LogBackendFactoryImpl::class, ThreemaApplication.Companion::class)
        .ignoreDependency(DebugLogFileBackend.Companion::class, HandlerExecutor::class)
        .ignoreDependency(DebugLogFileBackend::class, HandlerExecutor::class)
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\.datatypes\\.PredefinedContact.*"),
            nameMatching("ch\\.threema\\.app\\.BuildFlavor.*"),
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.storage\\.databaseupdate\\.DatabaseUpdateToVersion122.*"),
            nameMatching("ch\\.threema\\.app\\.BuildFlavor.*"),
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.localcrypto\\.Version2MasterKeyFileManagerImpl"),
            nameMatching("ch\\.threema\\.app\\.files\\.FileExtensionsKt\\.*"),
        )

    @ArchTest
    val dataLayerAccess: ArchRule = ArchitectureDefinitions.getLayeredArchitecture()
        .whereLayer(ArchitectureDefinitions.DATA)
        .mayOnlyBeAccessedByLayers(
            ArchitectureDefinitions.APP,
            ArchitectureDefinitions.STORAGE,
            ArchitectureDefinitions.PROTOBUF,
        )

    @ArchTest
    val storageLayerAccess: ArchRule = ArchitectureDefinitions.getLayeredArchitecture()
        .whereLayer(ArchitectureDefinitions.STORAGE)
        .mayOnlyBeAccessedByLayers(ArchitectureDefinitions.APP, ArchitectureDefinitions.DATA)

    @ArchTest
    val localcryptoLayerAccess: ArchRule = ArchitectureDefinitions.getLayeredArchitecture()
        .whereLayer(ArchitectureDefinitions.LOCALCRYPTO)
        .mayOnlyBeAccessedByLayers(ArchitectureDefinitions.APP, ArchitectureDefinitions.DATA, ArchitectureDefinitions.STORAGE)

    @ArchTest
    val domainLayerAccess: ArchRule = ArchitectureDefinitions.getLayeredArchitecture()
        .whereLayer(ArchitectureDefinitions.DOMAIN)
        .mayOnlyBeAccessedByLayers(
            ArchitectureDefinitions.APP,
            ArchitectureDefinitions.DATA,
            ArchitectureDefinitions.STORAGE,
            ArchitectureDefinitions.LOCALCRYPTO,
        )

    @ArchTest
    val baseLayerAccess: ArchRule = ArchitectureDefinitions.getLayeredArchitecture()
        .whereLayer(ArchitectureDefinitions.BASE)
        .mayOnlyBeAccessedByLayers(
            ArchitectureDefinitions.APP,
            ArchitectureDefinitions.DATA,
            ArchitectureDefinitions.STORAGE,
            ArchitectureDefinitions.LOCALCRYPTO,
            ArchitectureDefinitions.DOMAIN,
            ArchitectureDefinitions.LOGGING,
            ArchitectureDefinitions.PROTOBUF,
        )

    private fun Architectures.LayeredArchitecture.ignoreDependency(origin: KClass<*>, target: KClass<*>): Architectures.LayeredArchitecture =
        ignoreDependency(origin.java, target.java)
}

package ch.threema.storage.databaseupdate

import ch.threema.app.BuildFlavor
import ch.threema.domain.types.IdentityString
import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion122(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        for ((identity, publicKey) in getPredefinedContacts()) {
            sqLiteDatabase.execSQL(
                "UPDATE contacts SET verificationLevel = ? WHERE identity = ? AND publicKey = ?",
                arrayOf(
                    2, // fully verified
                    identity,
                    publicKey,
                ),
            )
        }
    }

    private fun getPredefinedContacts(): List<Pair<IdentityString, ByteArray>> =
        when (BuildFlavor.current.buildEnvironment) {
            BuildFlavor.BuildEnvironment.LIVE -> getPredefinedContactsProduction()
            BuildFlavor.BuildEnvironment.SANDBOX -> getPredefinedContactsSandbox()
            BuildFlavor.BuildEnvironment.ONPREM -> emptyList()
        }

    private fun getPredefinedContactsProduction(): List<Pair<IdentityString, ByteArray>> = listOf(
        "*3MAPUSH" to "fd711e1a0db0e2f03fcaab6c43da2575b9513664a62a12bd0728d87f7125cc24".hexToByteArray(),
        "*3MATOKN" to "04884d12d668f855d00d71fb1d9d413c95f271312f7e077846af671875c4101b".hexToByteArray(),
        "*3MAW0RK" to "c0e8ad0f50c5c7315c402d3dc26db169408c117613e9b852d3d6c0e87fca536b".hexToByteArray(),
        "*3MAWORK" to "9aa0a72a8fb6f0cc53727fea6096f1b7b0ebefcc2650ad39a1e54837bba0bc4b".hexToByteArray(),
        "*BETAFBK" to "5684d6dcd32a16488df8371095fc9a1fc25baeb6b97366d99fdf2aba00e2bc5c".hexToByteArray(),
        "*MY3DATA" to "3b01854f24736e2d0d2dc387eaf2c0273c5049052147132369bf3960d0a0bf02".hexToByteArray(),
        "*SUPPORT" to "0f944d18324b2132c61d8e40afce60a0ebd701bb11e89be94972d4229e94722a".hexToByteArray(),
        "*THREEMA" to "3a38650c681435bd1fb8498e213a2919b09388f5803aa44640e0f706326a865c".hexToByteArray(),
    )

    private fun getPredefinedContactsSandbox(): List<Pair<IdentityString, ByteArray>> = listOf(
        "*3MAPUSH" to "fd711e1a0db0e2f03fcaab6c43da2575b9513664a62a12bd0728d87f7125cc24".hexToByteArray(),
        "*3MAW0RK" to "c79d9e0f70342e653b0c6df027af8c8681db40e11bf556dd33ec78ee6f810c6d".hexToByteArray(),
        "*3MAWORK" to "9aa0a72a8fb6f0cc53727fea6096f1b7b0ebefcc2650ad39a1e54837bba0bc4b".hexToByteArray(),
        "*MY3DATA" to "83adfee6558b68ae3cd6bbe2a33f4e4409d5624a7cea23a18975aea6272a0070".hexToByteArray(),
        "*SUPPORT" to "0f944d18324b2132c61d8e40afce60a0ebd701bb11e89be94972d4229e94722a".hexToByteArray(),
        "*THREEMA" to "3a38650c681435bd1fb8498e213a2919b09388f5803aa44640e0f706326a865c".hexToByteArray(),
    )

    override fun getDescription() = "update verification level of predefined contacts"

    override val version = 122
}
